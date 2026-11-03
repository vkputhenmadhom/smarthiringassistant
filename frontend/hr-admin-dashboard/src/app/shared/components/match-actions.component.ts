import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Apollo } from 'apollo-angular';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { UPDATE_MATCH_STATUS_MUTATION } from '../../graphql/queries';
import { CandidateMatch, MatchStatus } from '../models';

/**
 * Review actions for one match: Shortlist / Hire / Reject, and Move back to undo. A note (optional) goes with the
 * next action. Shortlisting and hiring notify the candidate in the app; rejecting does not.
 */
@Component({
  selector: 'sha-match-actions',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <div class="actions">
      <ng-container *ngIf="isNew">
        <button mat-stroked-button color="primary" [disabled]="busy" (click)="set('SHORTLISTED')"
                matTooltip="Shortlist and let the candidate know">Shortlist</button>
        <button mat-button color="warn" [disabled]="busy" (click)="set('REJECTED')">Reject</button>
      </ng-container>
      <ng-container *ngIf="match.status === 'SHORTLISTED'">
        <button mat-stroked-button color="primary" [disabled]="busy" (click)="set('HIRED')"
                matTooltip="Mark as hired and let the candidate know">Hire</button>
        <button mat-button color="warn" [disabled]="busy" (click)="set('REJECTED')">Reject</button>
        <button mat-button [disabled]="busy" (click)="set('MATCHED')" matTooltip="Back to New">Move back</button>
      </ng-container>
      <ng-container *ngIf="match.status === 'REJECTED' || match.status === 'HIRED'">
        <button mat-button [disabled]="busy" (click)="set(match.status === 'HIRED' ? 'SHORTLISTED' : 'MATCHED')"
                [matTooltip]="match.status === 'HIRED' ? 'Back to Shortlisted' : 'Back to New'">Move back</button>
      </ng-container>
      <button mat-icon-button (click)="noteOpen = !noteOpen" [attr.aria-label]="noteOpen ? 'Hide note' : 'Add a note'"
              [matTooltip]="match.reviewerComments ? 'Note: ' + match.reviewerComments : 'Add a note to the next action'">
        <mat-icon [class.has-note]="!!match.reviewerComments">sticky_note_2</mat-icon>
      </button>
    </div>
    <input *ngIf="noteOpen" class="note" [(ngModel)]="note" placeholder="Note for this decision (optional)"
           aria-label="Note for this decision" maxlength="1000">
    <div class="error" *ngIf="error" role="alert">{{ error }}</div>
  `,
  styles: [`
    .actions { display:flex; align-items:center; justify-content:flex-end; gap:.25rem; white-space:nowrap; }
    .note { width:100%; margin-top:.35rem; padding:.35rem .5rem; border:1px solid #ccc; border-radius:6px; font:inherit; font-size:.85rem; }
    .has-note { color:#f9a825; }
    .error { color:#c62828; font-size:.8rem; margin-top:.25rem; }
  `],
})
export class MatchActionsComponent {
  private apollo = inject(Apollo);

  @Input({ required: true }) match!: CandidateMatch;
  /** The match after its status changed */
  @Output() changed = new EventEmitter<CandidateMatch>();

  busy = false;
  noteOpen = false;
  note = '';
  error: string | null = null;

  get isNew() { return this.match.status === 'MATCHED' || this.match.status === 'PENDING'; }

  set(status: MatchStatus) {
    this.busy = true;
    this.error = null;
    this.apollo.mutate<any>({
      mutation: UPDATE_MATCH_STATUS_MUTATION,
      variables: { matchId: this.match.id, status, comment: this.note.trim() || null },
    }).subscribe({
      next: r => {
        const updated = r.data?.updateMatchStatus;
        this.busy = false;
        this.note = '';
        this.noteOpen = false;
        if (updated) this.changed.emit({ ...this.match, status: updated.status, reviewerComments: updated.reviewerComments });
      },
      error: err => { this.busy = false; this.error = err.message; },
    });
  }
}
