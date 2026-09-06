import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Role } from '../../../core/models/auth.model';
import { Employee } from '../../../core/models/employee.model';
import { EmployeeService } from '../../../core/services/employee.service';

/**
 * Admin screen for employee CRUD.
 *
 * One component handles list + create + edit. A separate "new employee" page and
 * "edit employee" page would duplicate the same eight fields and the same
 * validation twice; instead the form panel below the table is shown on demand and
 * `editingId` decides whether saving means POST or PUT.
 */
@Component({
  selector: 'app-admin-employees',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-employees.html',
})
export class AdminEmployees implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);

  readonly employees = signal<Employee[]>([]);
  /** When on, archived (soft-deleted) employees are listed as well. */
  readonly showArchived = signal(false);
  readonly loading = signal(true);
  readonly saving = signal(false);

  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  /** Per-field messages returned by the backend on a 400 - { email: "...", ... }. */
  readonly fieldErrors = signal<Record<string, string>>({});

  readonly formVisible = signal(false);
  /** null = creating a new employee, a number = editing that employee. */
  readonly editingId = signal<number | null>(null);

  /**
   * These validators mirror EmployeeRequest.java field for field.
   *
   * They exist for fast feedback, not for safety - the server validates the same
   * rules again, because anything enforced only here can be bypassed with devtools.
   */
  readonly form = this.formBuilder.nonNullable.group({
    employeeCode: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    department: ['', [Validators.required]],
    designation: ['', [Validators.required]],
    role: ['EMPLOYEE' as Role, [Validators.required]],
    active: [true],
  });

  ngOnInit(): void {
    this.loadEmployees();
  }

  toggleArchived(event: Event): void {
    this.showArchived.set((event.target as HTMLInputElement).checked);
    this.loadEmployees();
  }

  loadEmployees(): void {
    this.loading.set(true);
    this.employeeService.findAll(this.showArchived()).subscribe({
      next: (employees) => {
        this.employees.set(employees);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load employees.');
        this.loading.set(false);
      },
    });
  }

  startCreate(): void {
    this.editingId.set(null);
    this.clearMessages();
    this.form.reset({ role: 'EMPLOYEE', active: true });
    /*
     * Password is REQUIRED when creating. Note this mirrors the server exactly:
     * EmployeeService.create() rejects a blank password, while update() treats
     * blank as "keep the existing one".
     */
    this.form.controls.password.setValidators([Validators.required, Validators.minLength(6)]);
    this.form.controls.password.updateValueAndValidity();
    this.formVisible.set(true);
  }

  startEdit(employee: Employee): void {
    this.editingId.set(employee.id);
    this.clearMessages();
    this.form.reset({
      employeeCode: employee.employeeCode,
      name: employee.name,
      email: employee.email,
      password: '',
      department: employee.department,
      designation: employee.designation,
      role: employee.role,
      active: employee.active,
    });
    /*
     * Password is OPTIONAL when editing - blank means "leave the hash alone".
     * setValidators REPLACES the whole list, so required is dropped here and the
     * minLength rule only bites if the admin actually types something.
     */
    this.form.controls.password.setValidators([Validators.minLength(6)]);
    this.form.controls.password.updateValueAndValidity();
    this.formVisible.set(true);
  }

  cancel(): void {
    this.formVisible.set(false);
    this.clearMessages();
  }

  save(): void {
    this.clearMessages();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const value = this.form.getRawValue();
    const id = this.editingId();

    // Send no password field at all on an edit when it was left blank.
    const payload = { ...value, password: value.password ? value.password : undefined };

    const request$ = id
      ? this.employeeService.update(id, payload)
      : this.employeeService.create(payload);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.formVisible.set(false);
        this.successMessage.set(id ? 'Employee updated.' : 'Employee created.');
        this.loadEmployees();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.showServerError(error);
      },
    });
  }

  remove(employee: Employee): void {
    /*
     * A native confirm() rather than a custom modal - a hand-built modal would be
     * a lot of markup for one yes/no question.
     *
     * The wording matters. This is an ARCHIVE, not an erase - saying "delete"
     * without explaining would leave the admin believing they had destroyed the
     * record, and possibly avoiding the button when they should use it.
     */
    const confirmed = window.confirm(
      `Archive ${employee.name}?\n\n`
        + `They will be removed from the staff list and can no longer log in.\n`
        + `Their leave history is KEPT so HR can still trace it.`,
    );
    if (!confirmed) {
      return;
    }

    this.clearMessages();
    this.employeeService.delete(employee.id).subscribe({
      next: () => {
        this.successMessage.set(
          'Employee archived. Their leave history has been kept.');
        this.loadEmployees();
      },
      error: (error: HttpErrorResponse) => this.showServerError(error),
    });
  }

  hasError(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && control.touched;
  }

  /** Reads the server's message for a field, e.g. after a 409 duplicate email. */
  serverError(field: string): string | undefined {
    return this.fieldErrors()[field];
  }

  /**
   * Turns any failed response into something readable.
   *
   * Because every backend error uses the same ApiError shape, this one method
   * covers 400 validation failures, 409 duplicates and 403 denials alike.
   */
  private showServerError(error: HttpErrorResponse): void {
    this.fieldErrors.set(error.error?.fieldErrors ?? {});
    this.errorMessage.set(error.error?.message ?? 'Something went wrong. Please try again.');
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.fieldErrors.set({});
  }
}
