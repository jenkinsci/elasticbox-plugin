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
    var VARIABLE_TABLE_TEMPLATE = '<td><table style="width:100%; margin-left: 15px;"><tbody>' +
                '<tr><td class="setting-leftspace"><img height="16" width="16" src="{2}"></td>' + 
                '<td class="setting-leftspace" colspan="2"><div><b>{0} {1}</b></div></td></tr>' +
                '</tbody></table></td>',
        TEXT_VARIABLE_TEMPLATE = '<tr><td class="setting-leftspace">&nbsp;</td><td class="setting-name">{0}</td>' + 
                '<td class="setting-main"><input name="{0}" value="{1}" data-original-value="{2}" data-scope="{3}" class="setting-input eb-variable" type="text"></td>' +
                '<td>&nbsp;</td></tr>',
        BINDING_VARIABLE_TEMPLATE = '<tr><td class="setting-leftspace">&nbsp;</td><td class="setting-name">{0}</td>' + 
                '<td class="setting-main"><select name="{0}" value="{1}" data-original-value="{2}" data-scope="{3}" class="setting-input select eb-variable"></select></td>' +
                '<td>&nbsp;</td></tr>',

        Dom = YAHOO.util.Dom,
        Element = YAHOO.util.Element,
        Event = YAHOO.util.Event,
        Connect = YAHOO.util.Connect,
        
        getVariableHolderInfo = function (buildStepElement) {
            var type = Dom.getAttribute(buildStepElement, 'descriptorid') === ElasticBoxUtils.DeployBoxDescriptorId && 'profile' || 'instance'; 
            return {
                type: type,
                class: 'eb-' + type,
                changeListenerType: 'eb-' + type + '-change-listener'
            };
        },

        createVariableRow = function (variable, savedVariable, varTextBox, buildStep) {
            var row = document.createElement('tr'),
                variableTemplate;

            if (_.contains(['Text', 'Port', 'Password', 'Number', 'Binding'], variable.type)) {
                
                variableTemplate = variable.type === 'Binding' ? BINDING_VARIABLE_TEMPLATE : TEXT_VARIABLE_TEMPLATE;
                row.innerHTML = ElasticBoxUtils.format(variableTemplate, variable.name, savedVariable && savedVariable.value || variable.value, variable.value, variable.scope);
                Dom.getElementsByClassName('eb-variable', variable.type === 'Binding' && 'select' || 'input', row, function (variableInput) {
                    var updateBindingOptions = function () {
                            var deployBoxSteps = ElasticBoxUtils.getDeployBoxSteps(),
                                buildStepId;

                            if (Dom.getAttribute(buildStep, 'descriptorid') === ElasticBoxUtils.DeployBoxDescriptorId) {
                                buildStepId = ElasticBoxUtils.getBuildStepId(buildStep);
                                deployBoxSteps = _.reject(deployBoxSteps, function (step) {
                                    return step.id === buildStepId;
                                });
                            }

                            // remove existing options for deploy box steps
                            for (var child = Dom.getFirstChild(variableInput); 
                                    child !== null && ElasticBoxUtils.startsWith(child.getAttribute('value'), ElasticBoxUtils.DeployBoxDescriptorId); 
                                    child = Dom.getFirstChild(variableInput)) {
                                variableInput.removeChild(child);
                            } 
                            
                            variableInput.innerHTML = _.map(deployBoxSteps, function (step) {
                                        return ElasticBoxUtils.format('<option value="{0}">{1}</option>', step.id, step.name);
                                    }).join(' ') + variableInput.innerHTML;                                
                        },
                        
                        getInstancesUrl, workspaceSelect, fillUrl;
                            
                    Event.addListener(variableInput, 'change', function () {
                        var name = Dom.getAttribute(this, 'name'),
                            value = this.value,
                            scope = Dom.getAttribute(this, 'data-scope'),
                            boxVariables = Dom.getAttribute(varTextBox, 'value').evalJSON(),
                            modifiedVariable;

                        if (variable.type !== 'Binding' && value === Dom.getAttribute(this, 'data-original-value')) {
                            boxVariables = _.filter(boxVariables, function (variable) {
                                return variable.name === name && variable.scope === scope;
                            });
                        } else {
                            modifiedVariable = _.findWhere(boxVariables, { name: name, scope: scope });
                            if (modifiedVariable) {
                                modifiedVariable.value = value;
                            } else {
                                boxVariables.push({ name: name, value: value, scope: scope });
                            }
                        }
                        
                        Dom.setAttribute(varTextBox, 'value', boxVariables.toJSON());
                    });
                    if (variable.type === 'Binding') {
                        updateBindingOptions();
                        Event.addListener(variableInput, 'focus', updateBindingOptions);
                        
                        workspaceSelect = _.first(Dom.getElementsByClassName('eb-workspace', 'select', buildStep));
                        if (workspaceSelect.value) {
                            fillUrl = Dom.getAttribute(workspaceSelect, 'fillurl'),
                            getInstancesUrl = ElasticBoxUtils.format('{0}/getInstances?workspace={1}&box={2}', fillUrl.substring(0, fillUrl.lastIndexOf('/')), 
                                workspaceSelect.value, Dom.getAttribute(variableInput, 'data-original-value')); 
                            Connect.asyncRequest('GET', getInstancesUrl, {
                                success: function (response) {
                                    _.each(response.responseText.evalJSON(), function (instance) {
                                        var option = document.createElement('option');
                                        option.setAttribute("value", instance.id);
                                        option.innerHTML = instance.name;
                                        variableInput.appendChild(option);
                                    });
                                },

                                failure: function (response) {

                                }
                            });
                        }
                    }
                    
                });
            } else {
                return null;
            }

            return row;
        },
        
        addVariables = function (boxes, varTBodyElement, varTextBox, savedVariables, buildStep) {
            _.each(boxes, function (box) {
                var varTableRow = document.createElement('tr'),
                    varTableBody, scope;

                if (box.variables.length > 0) {
                    scope = _.first(box.variables).scope;
                    scope = scope ? '(' + scope + ')' : ' ';
                    varTableRow.innerHTML = ElasticBoxUtils.format(VARIABLE_TABLE_TEMPLATE, box.name, scope, box.icon);
                    varTBodyElement.appendChild(varTableRow);
                    varTableBody = Dom.getElementBy(function() { return true; }, 'tbody', varTableRow);
                    _.each(box.variables, function (variable) {
                        var savedVariable = savedVariables && _.findWhere(savedVariables, { name: variable.name,  scope: variable.scope }) || null,                
                            row = createVariableRow(variable, savedVariable, varTextBox, buildStep);

                        if (row) {
                            varTableBody.appendChild(row);
                        }
                    });
                }                
            });
        },
        
        refreshVariables = function (variableHolderSelect, varTBody, varTextBox, variableHolderType, buildStep, populate) {
            var varHeader = _.first(Dom.getChildren(varTBody)),
                varTBodyElement = new Element(varTBody),
                fillUrl = Dom.getAttribute(variableHolderSelect, 'fillurl'),
                variableHolderId = variableHolderSelect.value,
                varUrl, descriptorUrl;

            Dom.addClass(varHeader, 'eb-header');

            if (!Dom.getAttribute(varTextBox, 'value')) {
                Dom.setAttribute(varTextBox, 'value', '[]');
            }
            
            if (variableHolderId !== null) {
                descriptorUrl = fillUrl.substring(0, fillUrl.lastIndexOf('/'));
                varUrl = ElasticBoxUtils.format('{0}/getBoxStack?{1}={2}', descriptorUrl, variableHolderType, variableHolderId);
                Connect.asyncRequest('GET', varUrl, {
                    success: function (response) {
                        var savedVariables = populate ? Dom.getAttribute(varTextBox, 'value').evalJSON() : null;

                        if (!populate) {
                            _.each(_.rest(Dom.getChildren(varTBody)), function (row) {
                                varTBodyElement.removeChild(row);
                            });
                        }
                        
                        addVariables(response.responseText.evalJSON(), varTBodyElement, varTextBox, savedVariables, buildStep);
                    },

                    failure: function (response) {
                        //TODO: report error
                    }
                });   
            }
        },
        
        populateVariables = function (variableHolderSelect, varTBody, varTextBox, variableHolderType, buildStep) {
            var varHeader = _.first(Dom.getChildren(varTBody));
            
            
            if (!variableHolderSelect.value || Dom.hasClass(varHeader, 'eb-header')) {
                return;
            }
            
            refreshVariables(variableHolderSelect, varTBody, varTextBox, variableHolderType, buildStep, true);
        },
        
        initialize = function () {
            Dom.getElementsByClassName('eb-variable-inputs', 'tbody', document, 
                function (tbody) {
                    var builder = Dom.getAncestorBy(tbody, function (element) {
                            var descriptorId = Dom.getAttribute(element, 'descriptorid');
                            return  descriptorId === ElasticBoxUtils.DeployBoxDescriptorId || 
                                    descriptorId === 'com.elasticbox.jenkins.builders.ReconfigureBox';
                        }),
                        builderElement = new Element(builder),                    
                        varTBody = _.first(builderElement.getElementsByClassName('eb-variable-inputs', 'tbody')),
                        varTextBox = _.first(builderElement.getElementsByClassName('eb-variables', 'input')),
                        variableHolderInfo = getVariableHolderInfo(builder),
                        varTR;

                    if (varTBody && varTextBox) {
                        varTR = Dom.getAncestorByTagName(varTextBox, 'tr');
                        Dom.setAttribute(Dom.getAncestorByTagName(varTextBox, 'tr'), 'style', 'display:none');
                        Dom.getElementsByClassName(variableHolderInfo.class, 'select', builder, function (element) {
                            populateVariables(element, varTBody, varTextBox, variableHolderInfo.type, builder);
                            
                            if (!_.some(Event.getListeners(element, 'change'), function (listener) {
                                return listener.obj === variableHolderInfo.changeListenerType;
                            })) {
                                Event.addListener(element, 'change', function () {
                                    refreshVariables(this, varTBody, varTextBox, variableHolderInfo.type, builder);
                                }, variableHolderInfo.changeListenerType);

                            }                            
                        });
                    }
                });            
        };

    setTimeout(initialize, 1000);

})();
