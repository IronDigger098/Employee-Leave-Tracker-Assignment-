import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LeaveRequest } from '../../../core/models/leave.model';
import { LeaveService } from '../../../core/services/leave.service';

/**
 * The employee's own leave requests.
 *
 * Calls GET /api/leaves/my, which takes the employee id from the JWT. There is no
 * id in the URL and none in the request body, so this page cannot be pointed at
 * a colleague's data.
 */
@Component({
  selector: 'app-employee-leaves',
  imports: [DatePipe, RouterLink],
  templateUrl: './employee-leaves.html',
})
export class EmployeeLeaves implements OnInit {
  private readonly leaveService = inject(LeaveService);

  readonly leaves = signal<LeaveRequest[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadLeaves();
  }

  loadLeaves(): void {
    this.loading.set(true);
    this.leaveService.findMine().subscribe({
      next: (leaves) => {
        this.leaves.set(leaves);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load your leave requests.');
        this.loading.set(false);
      },
    });
  }

  /**
   * Withdraw a request.
   *
   * The button only appears on PENDING rows, but that is a convenience, not the
   * rule - LeaveService.delete() on the server independently refuses to remove a
   * request that has already been reviewed, and refuses one that belongs to
   * somebody else. The UI hiding a button is never what makes an action safe.
   */
  withdraw(leave: LeaveRequest): void {
    const confirmed = window.confirm('Withdraw this leave request?');
    if (!confirmed) {
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.leaveService.delete(leave.id).subscribe({
      next: () => {
        this.successMessage.set('Leave request withdrawn.');
        this.loadLeaves();
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(error.error?.message ?? 'Could not withdraw the request.');
      },
    });
  }
}
