import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Navbar } from './layout/navbar/navbar';

/**
 * The root component - the application shell.
 *
 * It holds only the navbar and a <router-outlet>. Every actual page is rendered
 * into that outlet by the router, so this file never changes as pages are added.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Navbar],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
