import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DashboardStats } from '../../../core/models/employee.model';
import { LEAVE_TYPES, LeaveRequest, LeaveType } from '../../../core/models/leave.model';
import { EmployeeService } from '../../../core/services/employee.service';
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
/**
 * Today as yyyy-MM-dd in the BROWSER'S OWN timezone.
 *
 * Deliberately not `new Date().toISOString().substring(0, 10)`, which was the
 * original version and was wrong: toISOString() always converts to UTC first. At
 * 3am in Dhaka (UTC+6) it still returns yesterday's date, so the validator happily
 * accepted a start date that had already passed.
 *
 * Building the string from the local getFullYear/getMonth/getDate components keeps
 * it on the user's calendar day. padStart matters because getMonth() is 0-based and
 * returns 8 for September - "2026-9-6" would compare wrongly as a string against
 * "2026-09-07".
 */
function localToday(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

const notInPastValidator = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string;
  if (!value) {
    return null; // Validators.required already covers an empty field
  }
  // Both are yyyy-MM-dd, and ISO date strings compare correctly as strings.
  return value < localToday() ? { pastDate: true } : null;
};

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
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './leave-form.html',
})
export class LeaveForm implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly leaveService = inject(LeaveService);
  private readonly employeeService = inject(EmployeeService);
  private readonly router = inject(Router);

  readonly leaveTypes = LEAVE_TYPES;
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});

  /** Leave balance, loaded on open so the user can see it before choosing dates. */
  readonly stats = signal<DashboardStats | null>(null);

  /**
   * The employee's existing requests, used to warn about a date clash before they
   * submit. Only PENDING and APPROVED can clash - a rejected leave blocks nothing,
   * which is the same rule the server applies.
   */
  private readonly myLeaves = signal<LeaveRequest[]>([]);

  /**
   * Fetch the balance when the page opens.
   *
   * If it fails we simply do not show the hint - the form still works, and the
   * server enforces the entitlement regardless. A failed nice-to-have should
   * never block the main action.
   */
  ngOnInit(): void {
    this.employeeService.employeeStats().subscribe({
      next: (stats) => this.stats.set(stats),
      error: () => this.stats.set(null),
    });

    this.leaveService.findMine().subscribe({
      next: (leaves) =>
        this.myLeaves.set(leaves.filter((leave) => leave.status !== 'REJECTED')),
      error: () => this.myLeaves.set([]),
    });
  }

  /**
   * The existing request the chosen dates clash with, or null.
   *
   * Same overlap test as the server's repository query, and it reads more clearly
   * as its negation: two ranges do NOT overlap when one ends before the other
   * starts. Anything else is a clash.
   *
   * ISO date strings compare correctly with <= and >=, so no Date objects needed.
   */
  get clashingLeave(): LeaveRequest | null {
    const { startDate, endDate } = this.form.getRawValue();
    if (!startDate || !endDate || endDate < startDate) {
      return null;
    }
    return (
      this.myLeaves().find(
        (leave) => leave.startDate <= endDate && leave.endDate >= startDate,
      ) ?? null
    );
  }

  /**
   * Length of the requested leave in days, counting both ends - the same +1 rule
   * the server uses in LeaveService.lengthInDays().
   *
   * A getter rather than a computed signal because it reads from the reactive
   * form, which is not signal-based; Angular re-evaluates it during change
   * detection as the user edits the dates.
   */
  get requestedDays(): number {
    const { startDate, endDate } = this.form.getRawValue();
    if (!startDate || !endDate || endDate < startDate) {
      return 0;
    }
    const millisPerDay = 24 * 60 * 60 * 1000;
    const span = new Date(endDate).getTime() - new Date(startDate).getTime();
    return Math.round(span / millisPerDay) + 1;
  }

  /** True when the chosen dates would take the employee past their entitlement. */
  get exceedsBalance(): boolean {
    const balance = this.stats();
    return balance !== null && this.requestedDays > balance.leaveDaysRemaining;
  }

  /** Any reason the form should not be submittable, beyond ordinary validation. */
  get blocked(): boolean {
    return this.exceedsBalance || this.clashingLeave !== null;
  }

  /** Today as yyyy-MM-dd, used as the `min` on the date inputs. */
  /** Feeds [min] on the date inputs, so the native picker greys out past days. */
  readonly today = localToday();

  readonly form = this.formBuilder.nonNullable.group(
    {
      leaveType: ['CASUAL' as LeaveType, [Validators.required]],
      /*
       * notInPastValidator is a REAL validator, unlike the [min] attribute on the
       * input. Angular puts novalidate on forms it manages, so the browser's own
       * min enforcement never runs; [min] only greys out earlier dates in the
       * native picker, which a typed or pasted value walks straight past.
       */
      startDate: ['', [Validators.required, notInPastValidator]],
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
