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

        refresh = function (populate) {
            var deployBoxIndex = 1,
                buildSteps;
        
            Dom.getElementsBy(function (element) {
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
                        setTimeout(refresh, 500);
                    }, ElasticBoxUtils.DeployBoxDescriptorId);

                }                            

                Dom.getElementsByClassName('eb-id', 'input', builder, function (input) {
                    Dom.setAttribute(Dom.getAncestorByTagName(input, 'tr'), 'style', 'display:none');
                    if (!Dom.getAttribute(input, 'value')) {
                        Dom.setAttribute(input, 'value', ElasticBoxUtils.DeployBoxDescriptorId + '-' + ElasticBoxUtils.uuid());
                    }
                });

            });
                     
            buildSteps = Dom.getElementsBy(function (element) {
                var descriptorId = Dom.getAttribute(element, 'descriptorid');
                return descriptorId !== ElasticBoxUtils.DeployBoxDescriptorId && ElasticBoxUtils.startsWith(descriptorId, ElasticBoxUtils.DescriptorIdPrefix);
            }, 'div', document);
            
            _.each(buildSteps, function (buildStep) {
                var getOptions = function () {
                            return _.map(ElasticBoxUtils.getPriorDeployBoxSteps(buildStep), function (step) {
                                return ElasticBoxUtils.format('<option value="{0}">{1}</option>', step.id, step.name);
                            }).join(' ');
                        },
                
                    descriptorId = Dom.getAttribute(buildStep, 'descriptorid'),
                    buildStepSelect = _.first(Dom.getElementsByClassName('eb-buildstep', 'select', buildStep)),
                    selectedBuildStepId = buildStepSelect && buildStepSelect.value || null,
            
                    updateOptions = function (currentValue) {
                            var selectedOption;

                            buildStepSelect.innerHTML = getOptions();
                            selectedOption = Dom.getElementBy(function (option) {
                                return Dom.getAttribute(option, 'value') === currentValue;
                            }, 'option', buildStepSelect);
                            if (!selectedOption) {
                                selectedOption = _.first(Dom.getChildren(buildStepSelect));
                            }
                            buildStepSelect.selectedIndex = selectedOption ? Dom.getChildren(buildStepSelect).indexOf(selectedOption) : 0;                        
                        },
                        
                    populateOptions = function () {
                        var firstOption = Dom.getFirstChild(buildStepSelect);
                        
                        if (firstOption && Dom.getAttribute(firstOption, "value") === 'loading') {
                            updateOptions(selectedBuildStepId);
                        } else {
                            setTimeout(populateOptions, 500);
                        }
                    },
                        
                    priorBuildStepRadio, existingInstanceRadio, existingInstanceStartRow, priorBuildStepStartRow;
                
                if (buildStepSelect) {
                    if (populate && selectedBuildStepId) {
                        priorBuildStepRadio = Dom.getElementBy(function (element) {
                            return Dom.getAttribute(element, 'value') === 'eb-instance-from-prior-buildstep';
                        }, 'input', buildStep);
                        priorBuildStepRadio.checked = true;
                        Dom.setAttribute(Dom.getAncestorByTagName(buildStepSelect, 'tr'), 'style', '');
                        
                        existingInstanceRadio = Dom.getElementBy(function (element) {
                            return Dom.getAttribute(element, 'value') === 'eb-existing-instance';
                        }, 'input', buildStep);                        
                        existingInstanceStartRow = Dom.getAncestorByTagName(existingInstanceRadio, 'tr');
                        priorBuildStepStartRow = Dom.getAncestorByTagName(priorBuildStepRadio, 'tr');
                        for (var sibling = Dom.getNextSibling(existingInstanceStartRow); sibling && sibling !== priorBuildStepStartRow; sibling = Dom.getNextSibling(sibling)) {
                            Dom.setAttribute(sibling, 'style', 'display: none;');
                        }
                    }

                    populateOptions();
                    
                    if (!_.some(Event.getListeners(buildStepSelect, 'focus'), function (listener) {
                        return listener.obj === descriptorId;
                    })) {
                        Event.addListener(buildStepSelect, 'focus', function () {
                            updateOptions(this.value);
                        }, descriptorId);
                    }
                }
            });
        };

    setTimeout(function () {
        refresh(true);
    }, 500);

})();
