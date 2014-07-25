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

            ElasticBoxUtils.initializeBuildSteps();
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
            });
                     
            buildSteps = Dom.getElementsBy(function (element) {
                var descriptorId = Dom.getAttribute(element, 'descriptorid');
                return descriptorId !== ElasticBoxUtils.DeployBoxDescriptorId && ElasticBoxUtils.startsWith(descriptorId, ElasticBoxUtils.DescriptorIdPrefix);
            }, 'div', document);
            
            _.each(buildSteps, function (buildStep) {
                var getOptions = function () {
                        var options = ElasticBoxUtils.getPriorDeployBoxSteps(buildStep);

                        if (options.length === 0) {
                            options.push({ id: '', name: 'Not available' });
                        }

                        return _.map(options, function (step) {
                            return ElasticBoxUtils.format('<option value="{0}">{1}</option>', step.id, step.name);
                        }).join(' ');
                    },
                
                    descriptorId = Dom.getAttribute(buildStep, 'descriptorid'),
                    buildStepSelect = _.first(Dom.getElementsByClassName('eb-buildstep', 'select', buildStep)),
                    selectedBuildStepId = buildStepSelect && Dom.getAttribute(buildStepSelect, "value") || null,
            
                    updateOptions = function (currentValue, populate) {
                        var selectedOption;

                        buildStepSelect.innerHTML = getOptions();
                        if (currentValue) {
                            selectedOption = Dom.getElementBy(function (option) {
                                return Dom.getAttribute(option, 'value') === currentValue;
                            }, 'option', buildStepSelect);
                        }

                        if (!selectedOption) {
                            selectedOption = _.first(Dom.getChildren(buildStepSelect));
                        }
                        
                        buildStepSelect.value = selectedOption ? Dom.getAttribute(selectedOption, 'value') : null; 
                        if (populate || currentValue !== buildStepSelect.value) {
                            fireEvent(buildStepSelect, 'change');
                        } 
                    },
                    
                    /**
                     * Waits for the UI load and fill the options for build step (which is a single placeholder option 'loading')
                     * before updating the drop-down combo with valid build steps
                     */
                    populateOptions = function () {
                        var firstOption = Dom.getFirstChild(buildStepSelect),
                            value = firstOption ? Dom.getAttribute(firstOption, "value") : null;
                        
                        if (value && value !== 'loading' && value !== firstOption.innerText) {
                            return;
                        }
                        
                        if (Dom.getAttribute(firstOption, "value") === 'loading') {
                            updateOptions(selectedBuildStepId, true);
                        } else {
                            setTimeout(populateOptions, 100);
                        } 
                    },
                    
                    toggleInstanceType = function (existing) {
                        var priorBuildStepRadio = Dom.getElementBy(function (element) {
                                    return Dom.getAttribute(element, 'value') === 'eb-instance-from-prior-buildstep';
                                }, 'input', buildStep), 
                            existingInstanceRadio = Dom.getElementBy(function (element) {
                                    return Dom.getAttribute(element, 'value') === 'eb-existing-instance';
                                }, 'input', buildStep),                        
                            existingInstanceStartRow = Dom.getAncestorByTagName(existingInstanceRadio, 'tr'),
                            priorBuildStepStartRow = Dom.getAncestorByTagName(priorBuildStepRadio, 'tr'),
                            priorBuildStepStyle = existing ? 'display: none;' : '';
                        
                        existingInstanceRadio.checked = existing;
                        priorBuildStepRadio.checked = !existing;                    
                        Dom.setAttribute(Dom.getAncestorByTagName(buildStepSelect, 'tr'), 'style', priorBuildStepStyle);     
                        Dom.setAttribute(Dom.getNextSiblingBy(priorBuildStepStartRow, function (row) {
                            return Dom.getElementsByClassName('eb-variable-inputs', 'tbody', row).length > 0;
                        }), 'style', priorBuildStepStyle);

                        for (var sibling = Dom.getNextSibling(existingInstanceStartRow); sibling && sibling !== priorBuildStepStartRow; sibling = Dom.getNextSibling(sibling)) {
                            Dom.setAttribute(sibling, 'style', existing ? '' : 'display: none;');
                        }                        
                    };
                
                if (buildStepSelect) {
                    if (populate) {
                        toggleInstanceType(!selectedBuildStepId)
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
