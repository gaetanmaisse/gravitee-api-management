/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Component, Input, OnInit } from '@angular/core';

export interface RequestStatsSource {
  min: number;
  max: number;
  avg: number;
  rps: number;
  total: number;
}

@Component({
  selector: 'gio-request-stats',
  template: require('./gio-request-stats.component.html'),
  styles: [require('./gio-request-stats.component.scss')],
})
export class GioRequestStatsComponent implements OnInit {
  @Input()
  public source: RequestStatsSource;

  public data: RequestStatsSource;

  ngOnInit() {
    if (this.source) {
      this.data = {
        min: this.formatRequestTimeNumber(this.source.min),
        max: this.formatRequestTimeNumber(this.source.max),
        avg: this.formatRequestTimeNumber(this.source.avg),
        rps: this.formatNumber(this.source.rps, 1),
        total: this.formatNumber(this.source.total, 0),
      };
    }
  }

  formatRequestTimeNumber(num: number) {
    return this.formatNumber(num, num < 1 ? 2 : 0);
  }

  formatNumber(num: number, decimalPlaces: number): number {
    return num ? Number(num.toFixed(decimalPlaces)) : 0;
  }
}
