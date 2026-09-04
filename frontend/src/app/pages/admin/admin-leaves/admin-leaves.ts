import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { LeaveRequest, LeaveStatus } from '../../../core/models/leave.model';
import { LeaveService } from '../../../core/services/leave.service';

/**
 * Admin screen for reviewing leave requests.
 *
 * Loads every request via GET /api/leaves (ADMIN only) and offers Approve /
 * Reject on the ones still PENDING.
 */
@Component({
  selector: 'app-admin-leaves',
  imports: [DatePipe],
  templateUrl: './admin-leaves.html',
})
export class AdminLeaves implements OnInit {
  private readonly leaveService = inject(LeaveService);

  readonly leaves = signal<LeaveRequest[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  /** Id of the row whose approve/reject call is in flight, so only its buttons disable. */
  readonly busyId = signal<number | null>(null);

  readonly statusFilter = signal<LeaveStatus | 'ALL'>('ALL');

  /**
   * A COMPUTED signal: derived from `leaves` and `statusFilter`, and recalculated
   * automatically whenever either changes.
   *
   * Filtering in the browser is the right call here because the full list is
   * already loaded and small. On a real dataset this would be a query parameter
   * so the database does the filtering and the network carries less.
   */
  readonly visibleLeaves = computed(() => {
    const filter = this.statusFilter();
    const all = this.leaves();
    return filter === 'ALL' ? all : all.filter((leave) => leave.status === filter);
  });

  readonly pendingCount = computed(
    () => this.leaves().filter((leave) => leave.status === 'PENDING').length,
  );

  ngOnInit(): void {
    this.loadLeaves();
  }

  loadLeaves(): void {
    this.loading.set(true);
    this.leaveService.findAll().subscribe({
      next: (leaves) => {
        this.leaves.set(leaves);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load leave requests.');
        this.loading.set(false);
      },
    });
  }

  onFilterChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as LeaveStatus | 'ALL';
    this.statusFilter.set(value);
  }

  approve(leave: LeaveRequest): void {
    this.review(leave, 'APPROVED');
  }

  reject(leave: LeaveRequest): void {
    this.review(leave, 'REJECTED');
  }

  /**
   * Approve and reject differ only in which endpoint they call, so they share
   * one method - the same reason LeaveService.java has a single review() method.
   *
   * On success the server returns the UPDATED leave request, so we swap that row
   * in place rather than refetching the whole table. Fewer requests, and the row
   * shows exactly what the database now holds - including reviewedAt.
   */
  private review(leave: LeaveRequest, target: 'APPROVED' | 'REJECTED'): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.busyId.set(leave.id);

    const request$ =
      target === 'APPROVED' ? this.leaveService.approve(leave.id) : this.leaveService.reject(leave.id);

    request$.subscribe({
      next: (updated) => {
        this.leaves.update((current) =>
          current.map((item) => (item.id === updated.id ? updated : item)),
        );
        this.busyId.set(null);
        this.successMessage.set(
          `Request from ${updated.employeeName} ${target === 'APPROVED' ? 'approved' : 'rejected'}.`,
        );
      },
      error: (error: HttpErrorResponse) => {
        this.busyId.set(null);
        /*
         * Most likely cause here is a 400 "Only a PENDING leave request can be
         * reviewed" - somebody else reviewed it while this page was open. Showing
         * the server's own message and reloading puts the page back in sync.
         */
        this.errorMessage.set(error.error?.message ?? 'Could not update the request.');
        this.loadLeaves();
      },
    });
  }
}
