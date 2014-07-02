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
                '<td class="setting-main"><input name="{1}" value="{2}" data-original-value="{3}" data-scope="{4}" class="setting-input eb-variable" type="text"></td>' +
                '<td>&nbsp;</td></tr>',
        BINDING_VARIABLE_TEMPLATE = '<tr><td class="setting-leftspace">&nbsp;</td><td class="setting-name">{0}</td>' + 
                '<td class="setting-main"><select name="{1}" value="{2}" data-original-value="{3}" data-scope="{4}" class="setting-input select eb-variable"></select></td>' +
                '<td>&nbsp;</td></tr>',

        Dom = YAHOO.util.Dom,
        Element = YAHOO.util.Element,
        Event = YAHOO.util.Event,
        Connect = YAHOO.util.Connect,
        
        createVariableRow = function (variable, savedVariable, variableHolder) {
            var saveVariable = function (name, value, scope, type, varTextBox) {
                    var savedVariables = Dom.getAttribute(varTextBox, 'value').evalJSON(),
                        modifiedVariable;

                    if (type !== 'Binding' && value === Dom.getAttribute(this, 'data-original-value')) {
                         savedVariables = _.filter( savedVariables, function (savedVar) {
                            return savedVar.name === name && savedVar.scope === scope;
                        });
                    } else {
                        modifiedVariable = _.findWhere( savedVariables, { name: name, scope: scope });
                        if (modifiedVariable) {
                            modifiedVariable.value = value;
                        } else {
                             savedVariables.push({ name: name, value: value, scope: scope, type: type });
                        }
                    }

                    Dom.setAttribute(varTextBox, 'value',  savedVariables.toJSON());
                },
                
                row = document.createElement('tr'),
                variableTemplate;

            variableTemplate = variable.type === 'Binding' ? BINDING_VARIABLE_TEMPLATE : TEXT_VARIABLE_TEMPLATE;
            row.innerHTML = ElasticBoxUtils.format(variableTemplate, variable.name, 'eb.' + variable.name, savedVariable && savedVariable.value || variable.value, variable.value, variable.scope);
            Dom.getElementsByClassName('eb-variable', variable.type === 'Binding' && 'select' || 'input', row, function (variableInput) {
                var savedValue = Dom.getAttribute(variableInput, 'value'),

                    updateBindingOptions = function (currentValue) {
                        var scope = Dom.getAttribute(variableInput, 'data-scope'),
                            deployBoxSteps = variableHolder.getPriorDeployBoxSteps(),
                            selectedOption;

                        if (!currentValue) {
                            currentValue = variableInput.value;
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

                        selectedOption = Dom.getElementBy(function (option) {
                            return Dom.getAttribute(option, 'value') === currentValue;
                        }, 'option', variableInput);
                        if (!selectedOption) {
                            selectedOption = _.first(Dom.getChildren(variableInput));
                            saveVariable(variable.name, Dom.getAttribute(selectedOption, 'value'), scope, variable.type, variableHolder.varTextBox);
                        }
                        variableInput.selectedIndex = selectedOption ? Dom.getChildren(variableInput).indexOf(selectedOption) : 0;
                    },

                    getInstancesUrl, fillUrl, savedValue;

                Event.addListener(variableInput, 'change', function () {
                    saveVariable(variable.name, this.value, Dom.getAttribute(this, 'data-scope'), variable.type, variableHolder.varTextBox)
                });
                if (variable.type === 'Binding') {
                    variableInput.innerHTML = '<option value="loading">Loading...</option>'

                    Event.addListener(variableInput, 'focus', function () {
                        updateBindingOptions(this.value);
                    });

                    if (variableHolder.workspaceSelect.value) {
                        fillUrl = Dom.getAttribute(variableHolder.workspaceSelect, 'fillurl'),
                        getInstancesUrl = ElasticBoxUtils.format('{0}/getInstances?workspace={1}&box={2}', fillUrl.substring(0, fillUrl.lastIndexOf('/')), 
                            variableHolder.workspaceSelect.value, Dom.getAttribute(variableInput, 'data-original-value')); 
                        Connect.asyncRequest('GET', getInstancesUrl, {
                            success: function (response) {
                                variableInput.innerHTML = '';
                                _.each(response.responseText.evalJSON(), function (instance) {
                                    var option = document.createElement('option');

                                    option.setAttribute("value", instance.id);
                                    option.innerHTML = instance.name;
                                    variableInput.appendChild(option);                                            
                                });
                                updateBindingOptions(savedValue);
                            },

                            failure: function (response) {

                            }
                        });
                    }
                }

            });

            return row;
        },
        
        addVariables = function (boxes, savedVariables, variableHolder) {
            _.each(boxes, function (box) {
                var variables = _.reject(box.variables, function (variable) {
                        return _.contains(['Box', 'File'], variable.type);
                    }),
                    varTableRow, varTableBody, scope;

                if (variables.length > 0) {
                    varTableRow = document.createElement('tr');
                    scope = _.first(box.variables).scope;
                    scope = scope ? '(' + scope + ')' : ' ';
                    varTableRow.innerHTML = ElasticBoxUtils.format(VARIABLE_TABLE_TEMPLATE, box.name, scope, box.icon);
                    variableHolder.varTBody.appendChild(varTableRow);
                    varTableBody = Dom.getElementBy(function() { return true; }, 'tbody', varTableRow);
                    _.each(variables, function (variable) {
                        var savedVariable = savedVariables && _.findWhere(savedVariables, { name: variable.name,  scope: variable.scope }) || null,                
                            row = createVariableRow(variable, savedVariable, variableHolder);

                        if (row) {
                            varTableBody.appendChild(row);
                        }
                    });
                }                
            });
        },
        
        refreshVariables = function (variableHolder, populate) {
            var varHeader = _.first(Dom.getChildren(variableHolder.varTBody)),
                varTBodyElement = new Element(variableHolder.varTBody),
                fillUrl = Dom.getAttribute(variableHolder.select, 'fillurl'),
                variableHolderId = variableHolder.select.value,
                
                clearVariables = function () {
                    _.each(_.rest(Dom.getChildren(variableHolder.varTBody)), function (row) {
                        varTBodyElement.removeChild(row);
                    });
                    Dom.setAttribute(variableHolder.varTextBox, 'value', '[]');
                },
                
                varUrl, descriptorUrl;

            Dom.addClass(varHeader, 'eb-header');

            if (!Dom.getAttribute(variableHolder.varTextBox, 'value')) {
                Dom.setAttribute(variableHolder.varTextBox, 'value', '[]');
            }
            
            if (variableHolderId) {
                descriptorUrl = fillUrl.substring(0, fillUrl.lastIndexOf('/'));
                varUrl = ElasticBoxUtils.format('{0}/getBoxStack?{1}={2}', descriptorUrl, variableHolder.info.type, variableHolderId);
                Connect.asyncRequest('GET', varUrl, {
                    success: function (response) {
                        var savedVariables = populate ? Dom.getAttribute(variableHolder.varTextBox, 'value').evalJSON() : null;

                        if (!populate) {
                            clearVariables();
                        }
                        
                        addVariables(response.responseText.evalJSON(), savedVariables, variableHolder);
                    },

                    failure: function (response) {
                        //TODO: report error
                    }
                });   
            } else {
                clearVariables();
            }
        },
        
        populateVariables = function (variableHolder) {
            var varHeader = _.first(Dom.getChildren(variableHolder.varTBody));
            
            
            if (!variableHolder.select.value || Dom.hasClass(varHeader, 'eb-header')) {
                return;
            }
            
            refreshVariables(variableHolder, true);
        },
        
        initialize = function () {
            var getVariableHolders = function () {
                var variableHolders = [],
                    
                    getVariableHolderInfo = function (type) {
                        return {
                            type: type,
                            class: 'eb-' + type,
                            changeListenerType: 'eb-' + type + '-change-listener'
                        };
                    };
            
                Dom.getElementsByClassName('eb-variable-inputs', 'tbody', document, 
                    function (tbody) {
                        var variableHolderElement = Dom.getAncestorBy(tbody, function (element) {
                                var descriptorId = Dom.getAttribute(element, 'descriptorid');
                                return  descriptorId === ElasticBoxUtils.DeployBoxDescriptorId || 
                                        descriptorId === 'com.elasticbox.jenkins.builders.ReconfigureBox';
                            }),
                            
                            varTextBox, select, workspaceSelect, type, variableHolderInfo;
                        
                        if (variableHolderElement) {
                            varTextBox = _.first(Dom.getElementsByClassName('eb-variables', 'input', variableHolderElement));   
                            if (varTextBox) {
                                type = Dom.getAttribute(variableHolderElement, 'descriptorid') === ElasticBoxUtils.DeployBoxDescriptorId && 'profile' || 'instance';                             
                                variableHolderInfo = getVariableHolderInfo(type);
                            
                                //TODO: make variable holder a widget instead of exposing the HTML elements
                                variableHolders.push({
                                    info: variableHolderInfo,
                                    varTBody: tbody,
                                    varTextBox: varTextBox,
                                    select: _.first(Dom.getElementsByClassName(variableHolderInfo.class, 'select', variableHolderElement)),
                                    workspaceSelect: _.first(Dom.getElementsByClassName('eb-workspace', 'select', variableHolderElement)),
                                    getPriorDeployBoxSteps: function () {
                                        return ElasticBoxUtils.getPriorDeployBoxSteps(variableHolderElement);
                                    }
                                });
                            }
                        } else {
                            // variable holder is an InstanceCreator build wrapper
                            variableHolderElement = Dom.getAncestorByTagName(tbody, 'tr');
                            variableHolderElement = Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                varTextBox = _.first(Dom.getElementsByClassName('eb-variables', 'input', element));
                                return !_.isUndefined(varTextBox);
                            });
                            if (varTextBox) {
                                variableHolderInfo = getVariableHolderInfo('profile');
                                variableHolderElement = Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                    select = _.first(Dom.getElementsByClassName(variableHolderInfo.class, 'select', element));
                                    return !_.isUndefined(select);
                                });
                                Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                    workspaceSelect = _.first(Dom.getElementsByClassName('eb-workspace', 'select', element));
                                    return !_.isUndefined(workspaceSelect);
                                });
                                variableHolders.push({
                                    info: variableHolderInfo,
                                    varTBody: tbody,
                                    varTextBox: varTextBox,
                                    select: select,
                                    workspaceSelect: workspaceSelect,
                                    getPriorDeployBoxSteps: function () { return []; }
                                });
                            }
                        }                                                   
                    });

                return variableHolders;
            };
                    
            _.each(getVariableHolders(), function (variableHolder) {
                Dom.setAttribute(Dom.getAncestorByTagName(variableHolder.varTextBox, 'tr'), 'style', 'display:none');
                populateVariables(variableHolder);

                if (!_.some(Event.getListeners(variableHolder.select, 'change'), function (listener) {
                    return listener.obj === variableHolder.info.changeListenerType;
                })) {
                    Event.addListener(variableHolder.select, 'change', function () {
                        refreshVariables(variableHolder);
                    }, variableHolder.info.changeListenerType);

                }                            
            });
        };

    setTimeout(initialize, 500);

})();
