import { Component } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from '../../environments';
import { AuthenticateWithPasswordRequest } from '../shared/jsonrpc/request/authenticateWithPasswordRequest';
import { AuthenticateWithUsernameAndPasswordRequest } from '../shared/jsonrpc/request/authenticateWithUsernameAndPasswordRequest';
import { AuthenticateWithPasswordResponse } from '../shared/jsonrpc/response/authenticateWithPasswordResponse';
import { Edge, Service, Utils, Websocket } from '../shared/shared';

@Component({
  selector: 'index',
  templateUrl: './index.component.html'
})
export class IndexComponent {

  private static readonly EDGE_ID_REGEXP = new RegExp('\\d+');

  public env = environment;
  public form: FormGroup;
  public filter: string = '';
  public filteredEdges: Edge[] = [];

  private stopOnDestroy: Subject<void> = new Subject<void>();
  private slice: number = 20;

  constructor(
    public websocket: Websocket,
    public utils: Utils,
    private router: Router,
    private service: Service,
    private route: ActivatedRoute) {

    // Forwarding to device index if there is only 1 edge
    service.edges.pipe(takeUntil(this.stopOnDestroy)).subscribe(edges => {
      const edgeIds = Object.keys(edges);
      if (edgeIds.length === 1) {
        const edge = edges[edgeIds[0]];
        if (edge.isOnline) {
          this.router.navigate(['/device', edge.id]);
        }
      }
      this.updateFilteredEdges();
    });
  }

  ngOnInit() {
    this.service.setCurrentComponent('', this.route);
  }

  updateFilteredEdges() {
    let filter = this.filter.toLowerCase();
    let allEdges = this.service.edges.getValue();
    this.filteredEdges = Object.keys(allEdges)
      .filter(edgeId => {
        let edge = allEdges[edgeId];
        if (/* name */ edge.id.toLowerCase().includes(filter)
          || /* comment */ edge.comment.toLowerCase().includes(filter)) {
          return true;
        }
        return false;
      })
      .sort((edge1, edge2) => {
        // first: try to compare the number, e.g. 'edge5' < 'edge100'
        let e1match = edge1.match(IndexComponent.EDGE_ID_REGEXP)
        if (e1match != null) {
          let e2match = edge2.match(IndexComponent.EDGE_ID_REGEXP)
          if (e2match != null) {
            let e1 = Number(e1match[0]);
            let e2 = Number(e2match[0]);
            if (!isNaN(e1) && !isNaN(e2)) {
              return e1 - e2;
            }
          }
        }
        // second: apply 'natural sort'
        return edge1.localeCompare(edge2);
      })
      .map(edgeId => allEdges[edgeId]);
  }

  doLogin(password: string, username: string) {
    let request = new AuthenticateWithUsernameAndPasswordRequest({ password: password, username: username });
    this.websocket.sendRequest(request).then(response => {
      this.handleAuthenticateWithPasswordResponse(response as AuthenticateWithPasswordResponse);
    }).catch(reason => {
      console.error("Error on Login", reason);
    })
  }

  /**
   * Handles a AuthenticateWithPasswordResponse.
   * 
   * @param message 
   */
  private handleAuthenticateWithPasswordResponse(message: AuthenticateWithPasswordResponse) {
    this.service.handleAuthentication(message.result.token, message.result.edges);
  }

  doInfinite(infiniteScroll) {
    setTimeout(() => {
      this.slice += 5;
      infiniteScroll.target.complete();
    }, 200);
  }

  onDestroy() {
    this.stopOnDestroy.next();
    this.stopOnDestroy.complete();
  }
}
