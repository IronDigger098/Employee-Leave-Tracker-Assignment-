import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardStats } from '../../../core/models/employee.model';
import { AuthService } from '../../../core/services/auth.service';
import { EmployeeService } from '../../../core/services/employee.service';

/**
 * Employee landing page: the signed-in user's own counters.
 *
 * Calls GET /api/dashboard/employee, which scopes its counts by the id inside
 * the JWT. No employee id is sent from here, so there is nothing in the request
 * a user could edit to see someone else's totals.
 */
@Component({
  selector: 'app-employee-dashboard',
  imports: [RouterLink],
  templateUrl: './employee-dashboard.html',
})
export class EmployeeDashboard implements OnInit {
  private readonly employeeService = inject(EmployeeService);
  private readonly authService = inject(AuthService);

  readonly user = this.authService.currentUser;
  readonly stats = signal<DashboardStats | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.employeeService.employeeStats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load dashboard data.');
        this.loading.set(false);
      },
    });
  }
}
