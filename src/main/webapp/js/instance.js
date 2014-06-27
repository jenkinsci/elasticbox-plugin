/* 
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

(function() {
    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event,

        refresh = function () {
            var deployBoxIndex = 1,
        
                deployBoxStepElements = Dom.getElementsBy(function (element) {
                        return Dom.getAttribute(element, 'descriptorid') === ElasticBoxUtils.DeployBoxDescriptorId;
                    }, 'div', document, function (builder) {
                        var buildStepLabel = Dom.getElementBy(function (element) {
                                return ElasticBoxUtils.startsWith(element.innerHTML, ElasticBoxUtils.DeployBoxBuildStepName);
                            }, null, builder),

                            deleteButton = Dom.getElementBy(function () { return true; }, 'button', builder);

                        buildStepLabel.innerHTML = ElasticBoxUtils.format('{0} ({1})', ElasticBoxUtils.DeployBoxBuildStepName, deployBoxIndex++);
                        if (!_.some(Event.getListeners(deleteButton, 'click'), function (listener) {
                            return listener.obj === ElasticBoxUtils.DeployBoxDescriptorId;
                        })) {
                            Event.addListener(deleteButton, 'click', function () {
                                setTimeout(refresh, 1000);
                            }, ElasticBoxUtils.DeployBoxDescriptorId);

                        }                            

                        Dom.getElementsByClassName('eb-id', 'input', builder, function (input) {
                            Dom.setAttribute(Dom.getAncestorByTagName(input, 'tr'), 'style', 'display:none');
                            if (!Dom.getAttribute(input, 'value')) {
                                Dom.setAttribute(input, 'value', ElasticBoxUtils.DeployBoxDescriptorId + '-' + ElasticBoxUtils.uuid());
                            }
                        });

                    }),
                            
                 options = _.map(ElasticBoxUtils.getDeployBoxSteps(deployBoxStepElements), function (step) {
                        return ElasticBoxUtils.format('<option value="{0}">{1}</option>', step.id, step.name);
                    }).join(' ');
            
            Dom.getElementsBy(function (element) {
                var descriptorId = Dom.getAttribute(element, 'descriptorid');
                return descriptorId !== ElasticBoxUtils.DeployBoxDescriptorId && ElasticBoxUtils.startsWith(descriptorId, 'com.elasticbox.jenkins.builders.');
            }, 'div', document, function (buildStep) {
                Dom.getElementsByClassName('eb-buildstep', 'select', buildStep, function (select) {
                    select.innerHTML = options;
                });
            });
        };

    setTimeout(refresh, 1000);

})();
