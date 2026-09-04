import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * The login screen.
 *
 * Uses REACTIVE FORMS (FormBuilder + formGroup in the template) rather than
 * template-driven forms with ngModel. Reactive forms keep the form's shape and
 * its validation rules in TypeScript, where they are visible in one place and
 * type-checked, instead of scattered across HTML attributes.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /**
   * The form model. Each control is [initialValue, validators].
   *
   * These validators are for USER EXPERIENCE - instant feedback without a round
   * trip. They are not security: the same rules exist on the server in
   * LoginRequest.java, because anything enforced only in the browser can be
   * bypassed with devtools.
   */
  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  /** Local UI state. Signals re-render the template when they change. */
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  onSubmit(): void {
    this.errorMessage.set(null);

    /*
     * markAllAsTouched() so that error text appears on a blind submit.
     * Angular hides validation messages until a control is "touched", which
     * avoids screaming at the user before they have typed anything - but a
     * submit should reveal everything that is wrong.
     */
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);

    // getRawValue() returns a fully-typed object thanks to nonNullable above.
    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        // Role decides the destination: admins and employees have different homes.
        this.router.navigate([this.authService.homeRoute()]);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        /*
         * The backend sends our ApiError JSON shape, so error.error.message is
         * the human-readable sentence. The fallbacks cover the case where the
         * server is unreachable and there is no body at all.
         */
        this.errorMessage.set(
          error.error?.message ?? 'Login failed. Please check your connection and try again.',
        );
      },
    });
  }

  /** Small helper so the template can ask "should I show an error for this field?". */
  hasError(controlName: 'email' | 'password'): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && control.touched;
  }
}
