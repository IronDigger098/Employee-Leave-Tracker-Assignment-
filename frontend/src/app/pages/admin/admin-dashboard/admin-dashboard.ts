import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardStats } from '../../../core/models/employee.model';
import { AuthService } from '../../../core/services/auth.service';
import { EmployeeService } from '../../../core/services/employee.service';

/**
 * Admin landing page: company-wide counters.
 *
 * The numbers come from GET /api/dashboard/admin as a single small JSON object.
 * The alternative - downloading every leave request and counting in the browser -
 * would be slower and would ship rows the page never displays.
 */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink],
  templateUrl: './admin-dashboard.html',
})
export class AdminDashboard implements OnInit {
  private readonly employeeService = inject(EmployeeService);
  private readonly authService = inject(AuthService);

  readonly user = this.authService.currentUser;
  readonly stats = signal<DashboardStats | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  /**
   * ngOnInit is Angular's lifecycle hook for "the component is created and its
   * inputs are set - go and fetch data now".
   *
   * Data loading belongs here rather than in the constructor: a constructor
   * should build the object, not start network calls, and testing is far easier
   * when construction has no side effects.
   */
  ngOnInit(): void {
    this.employeeService.adminStats().subscribe({
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
