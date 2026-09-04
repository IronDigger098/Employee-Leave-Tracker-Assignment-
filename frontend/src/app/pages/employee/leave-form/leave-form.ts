import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LEAVE_TYPES, LeaveType } from '../../../core/models/leave.model';
import { LeaveService } from '../../../core/services/leave.service';

/**
 * Cross-field validator: end date must not be before start date.
 *
 * This one is attached to the FORM GROUP rather than to a control, because it
 * needs to see two fields at once - exactly the same reason the server checks
 * this rule in LeaveService.validateDates() instead of using an annotation on
 * LeaveRequestDto. A per-field annotation, or a per-control validator, can only
 * ever see its own value.
 *
 * Returning null means valid; returning an object marks the group invalid with
 * that key.
 */
const dateRangeValidator = (group: AbstractControl): ValidationErrors | null => {
  const start = group.get('startDate')?.value;
  const end = group.get('endDate')?.value;

  if (!start || !end) {
    return null; // the required validators already handle empty values
  }
  // ISO date strings ("2026-09-10") compare correctly with < and >.
  return end < start ? { dateRange: true } : null;
};

/**
 * The "apply for leave" form.
 *
 * Every rule here mirrors LeaveRequestDto.java plus the service's date check, so
 * the user gets instant feedback - but the server re-validates all of it, since
 * a browser check is a convenience and never a guarantee.
 */
@Component({
  selector: 'app-leave-form',
  imports: [ReactiveFormsModule],
  templateUrl: './leave-form.html',
})
export class LeaveForm {
  private readonly formBuilder = inject(FormBuilder);
  private readonly leaveService = inject(LeaveService);
  private readonly router = inject(Router);

  readonly leaveTypes = LEAVE_TYPES;
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});

  /** Today as yyyy-MM-dd, used as the `min` on the date inputs. */
  readonly today = new Date().toISOString().substring(0, 10);

  readonly form = this.formBuilder.nonNullable.group(
    {
      leaveType: ['CASUAL' as LeaveType, [Validators.required]],
      startDate: ['', [Validators.required]],
      endDate: ['', [Validators.required]],
      reason: ['', [Validators.required, Validators.maxLength(500)]],
    },
    // The second argument holds group-level options, including cross-field validators.
    { validators: dateRangeValidator },
  );

  /** True when both dates are filled in but the range is backwards. */
  get dateRangeInvalid(): boolean {
    return this.form.hasError('dateRange');
  }

  onSubmit(): void {
    this.errorMessage.set(null);
    this.fieldErrors.set({});

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);

    /*
     * Note what is NOT sent: no employeeId and no status. The server takes the
     * employee from the JWT and always sets PENDING - the payload type makes it
     * impossible to send either by accident.
     */
    this.leaveService.create(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/employee/leaves']);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.fieldErrors.set(error.error?.fieldErrors ?? {});
        this.errorMessage.set(error.error?.message ?? 'Could not submit your request.');
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/employee/leaves']);
  }

  hasError(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && control.touched;
  }

  serverError(field: string): string | undefined {
    return this.fieldErrors()[field];
  }
}
