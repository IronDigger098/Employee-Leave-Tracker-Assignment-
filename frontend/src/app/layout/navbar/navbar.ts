import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * Top navigation bar.
 *
 * Renders nothing when signed out, and shows admin or employee links depending
 * on the current user's role. Because AuthService.currentUser is a SIGNAL, the
 * template reacts automatically to login and logout - there is no subscription
 * to set up and none to tear down.
 *
 * `imports:` on the component itself is the standalone way: this component needs
 * routerLink, so it imports RouterLink. In the old NgModule world these would be
 * declared once in a shared module; now every component states its own needs,
 * which makes each file self-contained and lets the compiler drop what is unused.
 */
@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './navbar.html',
})
export class Navbar {
  private readonly authService = inject(AuthService);

  /** Exposed to the template. Signals are called as functions: user() */
  readonly user = this.authService.currentUser;
  readonly isAdmin = this.authService.isAdmin;

  logout(): void {
    this.authService.logout();
  }
}
