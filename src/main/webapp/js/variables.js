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

var ElasticBoxVariables = (function () {
    var VARIABLE_TABLE_TEMPLATE = '<td><table style="width:100%; margin-left: 15px;"><tbody>' +
                '<tr style="background-image: linear-gradient(to bottom, #ffffff, #eef0f2); ">' + 
                '<td class="setting-leftspace" colspan="3"><span style="cursor: pointer; ">&nbsp;' + 
                '<img src="{3}/expanded.png">&nbsp;' +
                '<img height="16" width="16" src="{2}">&nbsp;<b>{0} {1}</b></span></td></tr>' +
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
        
        variableHolders = [],
        imageFolder = null,
        
        getImageFolder = function () {
            var scriptElement, srcPath;
            
            if (imageFolder) {
                return imageFolder;
            }
            
            scriptElement = Dom.getElementBy(function (element) {
                var srcPath = Dom.getAttribute(element, "src");
                
                return srcPath && srcPath.indexOf('/plugin/elasticbox/js/') !== -1;
            }, 'script', document);
            if (scriptElement) {
                srcPath = Dom.getAttribute(scriptElement, "src");
                imageFolder = srcPath.substring(0, srcPath.indexOf('/plugin/elasticbox/js/')) + '/plugin/elasticbox/images';
            }
    
            return imageFolder;
        },
        
        addListeners = function (variableHolder) {
            var removeBuildStepListener = function () {
                    if (variableHolder.info.type === 'buildstep') {  
                        _.some(variableHolders, function (varHolder) {
                            return _.some(Event.getListeners(varHolder.select, 'change'), function (listener) {
                                if (listener.obj === variableHolder.buildStepId) {
                                    Event.removeListener(varHolder.select, 'change', listener.fn);
                                    return true;
                                }

                                return false;
                            });
                        });
                    }
                },

                addBuildStepListener = function () {
                    var buildStepVariableHolder;

                    if (variableHolder.info.type === 'buildstep') {                
                        buildStepVariableHolder = _.findWhere(variableHolders, { 'buildStepId': variableHolder.select.value });
                        if (buildStepVariableHolder) {
                            Event.addListener(buildStepVariableHolder.select, 'change', function () {
                                refreshVariables(variableHolder);
                            }, variableHolder.buildStepId);
                        }
                    }                                                                
                };

            if (!_.some(Event.getListeners(variableHolder.select, 'change'), function (listener) {
                return listener.obj === variableHolder.info.changeListenerType;
            })) {
                Event.addListener(variableHolder.select, 'change', function () {
                    if (this.value !== 'loading') {
                        removeBuildStepListener();
                        refreshVariables(variableHolder);
                        addBuildStepListener();                        
                    }
                }, variableHolder.info.changeListenerType);

                if (variableHolder.info.type === 'boxVersion') {
                    Event.addListener(variableHolder.profileSelect, 'change', function () {
                        refreshVariables(variableHolder);
                    });
                }

                addBuildStepListener();                   
            }
        },
        
        getBuildStepVariableHolder = function (buildStepId) {
            var variableHolder = _.findWhere(variableHolders, { buildStepId: buildStepId }),
                _variableHolders;
            
            if (_.isUndefined(variableHolder)) {
                // build step might have been added recently, find it again
                _variableHolders = getVariableHolders();
                _.chain(_variableHolders).filter(function (varHolder) {
                    return varHolder.buildStepId && _.isUndefined(_.findWhere(variableHolders, { buildStepId: varHolder.buildStepId }));
                }).each(function (varHolder) {
                    addListeners(varHolder);
                    if (varHolder.buildStepId === buildStepId) {
                        variableHolder = varHolder;
                    }
                });
            }
            
            return variableHolder;
        },

        getBoxStackUrl = function (variableHolder) {
            var variableHolderId = variableHolder.select.value,
                fillUrl, descriptorUrl;
        
            if (!variableHolderId) {
                return null;
            }
            
            if (variableHolder.info.type === 'buildstep') {
                variableHolder = getBuildStepVariableHolder(variableHolderId);
                return variableHolder ? getBoxStackUrl(variableHolder) : null;
            }
            
            fillUrl = Dom.getAttribute(variableHolder.select, 'fillurl');
            descriptorUrl = fillUrl.substring(0, fillUrl.lastIndexOf('/'));
            
            if (variableHolder.info.type === 'boxVersion') {
                return ElasticBoxUtils.format('{0}/getBoxStack?{1}={2}&box={3}', descriptorUrl, variableHolder.info.type, variableHolderId, variableHolder.boxSelect.value);
            } else {
                return ElasticBoxUtils.format('{0}/getBoxStack?{1}={2}', descriptorUrl, variableHolder.info.type, variableHolderId);
            }
        },
                
        getInstancesUrl = function (variableHolder, variableInput) {
            var fillUrl;
            
            if (variableHolder.info.type === 'buildstep') {
                variableHolder = getBuildStepVariableHolder(variableHolder.select.value);
                return variableHolder ? getBoxStackUrl(variableHolder) : null;
            }
            
            if (variableHolder.workspaceSelect && variableHolder.workspaceSelect.value) {
                fillUrl = Dom.getAttribute(variableHolder.workspaceSelect, 'fillurl');
                return ElasticBoxUtils.format('{0}/getInstances?workspace={1}&box={2}', fillUrl.substring(0, fillUrl.lastIndexOf('/')), 
                    variableHolder.workspaceSelect.value, Dom.getAttribute(variableInput, 'data-original-value')); 
            }
            
            return null;
        },
        
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
            if (_.isNull(variable.value) || _.isUndefined(variable.value)) {
                variable.value = '';
            }
            
            row.innerHTML = ElasticBoxUtils.format(variableTemplate, variable.name, '_' + variable.name, savedVariable && savedVariable.value || variable.value, variable.value, variable.scope);
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

                    instancesUrl, savedValue;

                Event.addListener(variableInput, 'change', function () {
                    saveVariable(variable.name, this.value, Dom.getAttribute(this, 'data-scope'), variable.type, variableHolder.varTextBox)
                });
                if (variable.type === 'Binding') {
                    variableInput.innerHTML = '<option value="loading">Loading...</option>'

                    Event.addListener(variableInput, 'focus', function () {
                        updateBindingOptions(this.value);
                    });

                    instancesUrl = getInstancesUrl(variableHolder, variableInput);
                    if (instancesUrl) {
                        Connect.asyncRequest('GET', instancesUrl, {
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
            var toggleVarTable = function (varTableRow) {
                    var varTableHeader = Dom.getElementBy(function() { return true; }, 'span', varTableRow),
                        stateImageElement = Dom.getFirstChild(varTableHeader),
                        expanded = Dom.getAttribute(stateImageElement, 'src').indexOf('/plugin/elasticbox/images/expanded.png') !== -1,
                        headerRow = Dom.getAncestorByTagName(varTableHeader, 'tr');

                    Dom.setAttribute(stateImageElement, 'src', expanded ? getImageFolder() + '/collapsed.png' : getImageFolder() + '/expanded.png');              
                    for (var row = Dom.getNextSibling(headerRow); row; row = Dom.getNextSibling(row)) {
                        Dom.setAttribute(row, 'style', expanded ? 'display: none' : '');
                    }
                },
            
                varTableRows = [];
            
            _.each(boxes, function (box) {
                var variables = _.reject(box.variables, function (variable) {
                        return _.contains(['Box', 'File'], variable.type);
                    }),
                    varTableRow, varTableHeader, varTableBody, scope;

                if (variables.length > 0) {
                    varTableRow = document.createElement('tr');
                    scope = _.first(box.variables).scope;
                    scope = scope ? '(' + scope + ')' : ' ';
                    varTableRow.innerHTML = ElasticBoxUtils.format(VARIABLE_TABLE_TEMPLATE, box.name, scope, box.icon, getImageFolder());
                    variableHolder.varTBody.appendChild(varTableRow);
                    varTableHeader = Dom.getElementBy(function() { return true; }, 'span', varTableRow);
                    Event.addListener(varTableHeader, 'click', function () {
                        toggleVarTable(varTableRow);
                    });
                    varTableBody = Dom.getElementBy(function() { return true; }, 'tbody', varTableRow);
                    _.each(variables, function (variable) {
                        var savedVariable = savedVariables && _.findWhere(savedVariables, { name: variable.name,  scope: variable.scope }) || null,                
                            row = createVariableRow(variable, savedVariable, variableHolder);

                        if (row) {
                            varTableBody.appendChild(row);
                        }
                    });
                    varTableRows.push(varTableRow);
                }                
            });
            
            _.each(_.rest(varTableRows), function (varTableRow) {
                toggleVarTable(varTableRow);
            });
        },
        
        refreshVariables = function (variableHolder, populate) {
            var varHeader = _.first(Dom.getChildren(variableHolder.varTBody)),
                varTBodyElement = new Element(variableHolder.varTBody),
                boxStackUrl = getBoxStackUrl(variableHolder),
                
                clearVariables = function () {
                    _.each(_.rest(Dom.getChildren(variableHolder.varTBody)), function (row) {
                        varTBodyElement.removeChild(row);
                    });
                    Dom.setAttribute(variableHolder.varTextBox, 'value', '[]');
                };

            Dom.addClass(varHeader, 'eb-header');

            if (!Dom.getAttribute(variableHolder.varTextBox, 'value')) {
                Dom.setAttribute(variableHolder.varTextBox, 'value', '[]');
            }
            
            if (boxStackUrl) {
                Connect.asyncRequest('GET', boxStackUrl, {
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
        
        getVariableHolderInfo = function (type) {
            return {
                type: type,
                class: 'eb-' + type,
                changeListenerType: 'eb-' + type + '-change-listener'
            };
        },
                
        getBuildStepVariableHolders = function (buildStepElement, varTBody) {
            var _variableHolders = [],
                descriptorId = Dom.getAttribute(buildStepElement, 'descriptorid'),
                varTextBoxes = Dom.getElementsByClassName('eb-variables', 'input', buildStepElement),
                varTextBox = _.first(varTextBoxes),
                buildStepId = ElasticBoxUtils.getBuildStepId(buildStepElement),
                
                select, variableHolderInfo, type;
        
            if (varTextBox) {
                select = _.first(Dom.getElementsByClassName('eb-boxVersion', 'select', buildStepElement));
                if (select) {
                    variableHolderInfo = getVariableHolderInfo('boxVersion');
                } else {
                    type = descriptorId === ElasticBoxUtils.DeployBoxDescriptorId && 'profile' || 'instance';                             
                    variableHolderInfo = getVariableHolderInfo(type);    
                    select = _.first(Dom.getElementsByClassName(variableHolderInfo.class, 'select', buildStepElement))
                }

                //TODO: make variable holder a widget instead of exposing the HTML elements
                _variableHolders.push({
                    buildStepId: buildStepId,
                    info: variableHolderInfo,
                    varTBody: varTBody,
                    varTextBox: varTextBox,
                    select: select,
                    workspaceSelect: _.first(Dom.getElementsByClassName('eb-workspace', 'select', buildStepElement)),
                    boxSelect: _.first(Dom.getElementsByClassName('eb-box', 'select', buildStepElement)),
                    profileSelect: _.first(Dom.getElementsByClassName('eb-profile', 'select', buildStepElement)),
                    getPriorDeployBoxSteps: function () {
                        return ElasticBoxUtils.getPriorDeployBoxSteps(buildStepElement);
                    }
                });

                if (descriptorId === ElasticBoxUtils.ReconfigureBoxDescriptorId) {
                    _variableHolders.push({
                        buildStepId: buildStepId,
                        info: getVariableHolderInfo('buildstep'),
                        varTBody: Dom.getElementsByClassName('eb-variable-inputs', 'tbody', buildStepElement)[1],
                        varTextBox: varTextBoxes[1],
                        select: _.first(Dom.getElementsByClassName('eb-buildstep', 'select', buildStepElement)),
                        getPriorDeployBoxSteps: function () {
                            return ElasticBoxUtils.getPriorDeployBoxSteps(buildStepElement);
                        }
                    });

                }
            }
            
            return _variableHolders;
        },

        getVariableHolders = function () {
            var _variableHolders = [];
            Dom.getElementsByClassName('eb-variable-inputs', 'tbody', document, 
                function (tbody) {
                    var variableHolderElement = Dom.getAncestorBy(tbody, function (element) {
                            var descriptorId = Dom.getAttribute(element, 'descriptorid');
                            return  descriptorId === ElasticBoxUtils.DeployBoxDescriptorId || 
                                    descriptorId === ElasticBoxUtils.ReconfigureBoxDescriptorId;
                        }),

                        varTextBox, select, workspaceSelect, variableHolderInfo, profileSelect, boxSelect;

                    if (variableHolderElement) {
                        _.each(getBuildStepVariableHolders(variableHolderElement, tbody), function (variableHolder) {
                            if (!_.findWhere(_variableHolders, { select: variableHolder.select })) {
                                _variableHolders.push(variableHolder);
                            }
                        });
                    } else {
                        // variable holder is an InstanceCreator build wrapper or Slave Configuration
                        variableHolderElement = Dom.getAncestorByTagName(tbody, 'tr');
                        variableHolderElement = Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                            varTextBox = _.first(Dom.getElementsByClassName('eb-variables', 'input', element));
                            return !_.isUndefined(varTextBox);
                        });
                        if (varTextBox) {
                            Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                select = _.first(Dom.getElementsByClassName('eb-boxVersion', 'select', element));
                                return !_.isUndefined(select);
                            });
                            if (select) {
                                variableHolderInfo = getVariableHolderInfo('boxVersion');
                            } else {
                                variableHolderInfo = getVariableHolderInfo('profile');
                                variableHolderElement = Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                    select = _.first(Dom.getElementsByClassName(variableHolderInfo.class, 'select', element));
                                    return !_.isUndefined(select);
                                });
                            }
                            Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                workspaceSelect = _.first(Dom.getElementsByClassName('eb-workspace', 'select', element));
                                return !_.isUndefined(workspaceSelect);
                            });
                            Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                profileSelect = _.first(Dom.getElementsByClassName('eb-profile', 'select', element));
                                return !_.isUndefined(profileSelect);
                            });
                            Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                boxSelect = _.first(Dom.getElementsByClassName('eb-box', 'select', element));
                                return !_.isUndefined(boxSelect);
                            });                            
                            _variableHolders.push({
                                info: variableHolderInfo,
                                varTBody: tbody,
                                varTextBox: varTextBox,
                                select: select,
                                workspaceSelect: workspaceSelect,
                                boxSelect: boxSelect,
                                profileSelect: profileSelect,
                                getPriorDeployBoxSteps: function () { return []; }
                            });
                        }
                    }                                                   
                });
                
            return _variableHolders;
        };

    return {
        initialize: function () {
            ElasticBoxUtils.initializeBuildSteps();
            variableHolders = getVariableHolders();
            _.each(variableHolders, function (variableHolder) {
                Dom.setAttribute(Dom.getAncestorByTagName(variableHolder.varTextBox, 'tr'), 'style', 'display: none;');
                populateVariables(variableHolder);
                addListeners(variableHolder);
            });
        }
    };
    
})();

(function() {
    setTimeout(ElasticBoxVariables.initialize, 500);
})();
