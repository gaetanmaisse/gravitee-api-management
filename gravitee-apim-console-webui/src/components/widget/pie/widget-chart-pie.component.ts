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
// eslint:disable-next-line:no-var-requires
require('@gravitee/ui-components/wc/gv-chart-pie');
const WidgetChartPieComponent: ng.IComponentOptions = {
  template: require('./widget-chart-pie.html'),
  bindings: {
    data: '<',
  },
  require: {
    parent: '^gvWidget',
  },
  /* @ngInject */
  controller: function widgetChartPieController($scope, $element) {
    this.$onInit = () => {
      $scope.$on('onWidgetResize', this.onResize.bind(this));
    };

    this.$onChanges = function (changes) {
      if (changes.data) {
        this.gvChartPie = $element.children()[0];
        this.options = {
          name: this.parent.widget.title,
          data: Object.keys(changes.data.currentValue.values || {}).map((label) => {
            // The next lines are weird and would need a complete refactor, it
            // will happen with the Angular migration of this component
            if (!this.parent.widget.chart.labels || !this.parent.widget.chart.labels.includes(label)) {
              return {
                name: label,
              };
            }

            const index = this.parent.widget.chart.labels.indexOf(label);
            return {
              name: this.parent.widget.chart.labels[index],
              color: this.parent.widget.chart.colors[index],
            };
          }),
        };

        const series = {
          values: changes.data.currentValue ? changes.data.currentValue.values : {},
        };

        // Send data to gv-chart-pie
        this.gvChartPie.setAttribute('series', JSON.stringify(series));
        this.gvChartPie.setAttribute('options', JSON.stringify(this.options));
      }
    };

    this.onResize = () => {
      this.options = {
        ...this.options,
        height: this.gvChartPie.offsetHeight,
        width: this.gvChartPie.offsetWidth,
      };
      this.gvChartPie.setAttribute('options', JSON.stringify(this.options));
    };
  },
};

export default WidgetChartPieComponent;
