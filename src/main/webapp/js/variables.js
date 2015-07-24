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
    var VARIABLE_TABLE_TEMPLATE = '<td><table style="width:100%; margin-left: 15px;"><tbody><tr>' +
                '<td class="setting-leftspace" colspan="4" style="background-image: linear-gradient(to bottom, #ffffff, #eef0f2); ">' +
                '<span style="cursor: pointer; ">&nbsp;<img src="{3}/expanded.png">&nbsp;' +
                '<img height="16" width="16" src="{2}">&nbsp;<b>{0} {1}</b></span></td></tr>' +
                '</tbody></table></td>',
        TEXT_VARIABLE_TEMPLATE = '<tr><td class="setting-leftspace">&nbsp;</td><td class="setting-name">{0}</td>' +
                '<td class="setting-main" colspan="2">' +
                '<input name="{1}" value="{2}" data-original-value="{3}" data-scope="{4}" class="setting-input eb-variable" type="{5}"></td>' +
                '<td><div>&nbsp;<a><img></a><div></td></tr>',
        BINDING_VARIABLE_TEMPLATE = '<tr><td class="setting-leftspace">&nbsp;</td><td class="setting-name">{0}</td>' +
                '<td class="setting-main" colspan="2">' +
                '<select name="{1}" value="{2}" data-original-value="{3}" data-scope="{4}" class="setting-input select eb-variable"></select>' +
                '<input name="{5}" value="{6}" class="eb-binding-tags setting-input" style="display: none;"></td>' +
                '<td><div>&nbsp;<a><img></a><div></td></tr>',

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

        toggleDeploymentOptions = function (variableHolder) {
            var showRows = function (select, show) {
                    for (var i = 0, tr = Dom.getAncestorByTagName(select, 'tr'); i < 2; i++) {
                        Dom.setAttribute(tr, 'style', show ? '' : 'display: none');
                        tr = Dom.getNextSibling(tr);
                    }
                },

                isCloudFormdation;


            if (variableHolder.profileSelect) {
                if (Dom.getStyle(Dom.getAncestorByTagName(variableHolder.boxSelect, 'tr'), 'display') !== 'none') {
                    isCloudFormdation = variableHolder.boxSelect.value && variableHolder.profileSelect.value === variableHolder.boxSelect.value;
                    variableHolder.cloudFormationSelected.value = isCloudFormdation ? 'true' : 'false';
                    showRows(variableHolder.profileSelect, !isCloudFormdation);
                    showRows(variableHolder.providerSelect, isCloudFormdation);
                    showRows(variableHolder.locationSelect, isCloudFormdation);
                    // show/hide radio options for deployment policy
                    Dom.getPreviousSiblingBy(Dom.getAncestorByTagName(variableHolder.profileSelect, 'tr'), function (sibling) {
                        var display = Dom.getStyle(sibling, 'display');

                        if (_.isUndefined(_.first(Dom.getElementsByClassName('section-header', 'div', sibling)))) {
                            // the sibling is not the section header yet, show/hide it depending on whether the selected box is a Cloud Formation box
                            if (isCloudFormdation) {
                                display = Dom.getStyle(sibling, 'display');
                                if (display !== 'none') {
                                    Dom.setStyle(sibling, 'display', 'none');
                                    Dom.addClass(sibling, 'ebx-hide');
                                }
                            } else if (Dom.hasClass(sibling, 'ebx-hide') && Dom.getStyle(sibling, 'display') === 'none') {
                                Dom.setAttribute(sibling, 'style', '');
                            }
                            return false;
                        }
                        return true;
                    });
                } else  {
                    showRows(variableHolder.providerSelect, false);
                    showRows(variableHolder.locationSelect, false);
                }
            }
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
                },

                descriptorElement;

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

                if (variableHolder.info.type === 'boxVersion' && variableHolder.boxSelect) {
                    Event.addListener(variableHolder.boxSelect, 'change', function () {
                        refreshVariables(variableHolder);
                    });
                }

                if (variableHolder.profileSelect) {
                    Event.addListener(variableHolder.profileSelect, 'change', function () {
                        toggleDeploymentOptions(variableHolder);
                    });
                }

                if (variableHolder.boxSelect) {
                    descriptorElement = ElasticBoxUtils.getDescriptorElement(variableHolder.boxSelect);
                    if (descriptorElement && Dom.getAttribute(descriptorElement, 'descriptorid') === ElasticBoxUtils.DeployBoxDescriptorId) {
                        Event.addListener(variableHolder.boxSelect, 'change', function () {
                            ElasticBoxUtils.updateDeployBoxLabel(descriptorElement);
                        });
                    }
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

        getCloudParameters = function (variableHolder) {
            return variableHolder.cloudSelect ? 'cloud=' + variableHolder.cloudSelect.value :
                ElasticBoxUtils.format('endpointUrl={0}&username={1}&password={2}&token={3}',
                    variableHolder.endpointUrlInput.value, variableHolder.usernameInput.value, variableHolder.passwordInput.value, variableHolder.tokenInput.value);
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
                return ElasticBoxUtils.format('{0}/getBoxStack?{1}={2}&box={3}&workspace={4}&{5}', descriptorUrl,
                    variableHolder.info.type, variableHolderId, variableHolder.boxSelect.value,
                    variableHolder.workspaceSelect.value, getCloudParameters(variableHolder));
            } else {
                return ElasticBoxUtils.format('{0}/getBoxStack?{1}={2}&{3}', descriptorUrl,
                    variableHolder.info.type, variableHolderId, getCloudParameters(variableHolder));
            }
        },

        getInstancesUrl = function (variableHolder, variableInput) {
            var fillUrl;

            if (variableHolder.info.type === 'buildstep') {
                variableHolder = getBuildStepVariableHolder(variableHolder.select.value);
                return variableHolder ? getBoxStackUrl(variableHolder) : null;
            }

            if (variableHolder.workspaceSelect && variableHolder.workspaceSelect.value) {
                fillUrl = Dom.getAttribute(variableHolder.boxSelect || variableHolder.workspaceSelect, 'fillurl');
                return ElasticBoxUtils.format('{0}/getInstances?workspace={1}&box={2}&{3}',
                    fillUrl.substring(0, fillUrl.lastIndexOf('/')),
                    variableHolder.workspaceSelect.value,
                    Dom.getAttribute(variableInput, 'data-original-value'),
                    getCloudParameters(variableHolder));
            }

            return null;
        },

        // variable decorators are the following:
        //   - a red mark to indicates that the variable is required
        //   - a trash bin icon to indicate that variable is modified, clicking on the trash bin will reset the variable
        toggleVariableDecorators = function (variableRow, variable) {
            var img = ElasticBoxUtils.getElementByTag('img', variableRow),
                div = ElasticBoxUtils.getElementByTag('div', variableRow),
                variableInput = _.first(Dom.getElementsByClassName('eb-variable', undefined, variableRow)),
                imageFolder = getImageFolder(),
                descriptorElement = ElasticBoxUtils.getDescriptorElement(variableRow);

            if (variable.type === 'Binding') {
                Dom.setAttribute(img, 'src', getImageFolder() + '/none.png');
            } else {
                if (variableInput.value !== Dom.getAttribute(variableInput, 'data-original-value')) {
                    Dom.setAttribute(variableInput, 'style', '');
                    Dom.setAttribute(img, 'src', imageFolder + '/reset.png');
                    Dom.setAttribute(img, 'style', 'cursor: pointer');
                    Event.addListener(img, 'click', function () {
                        variableInput.value = Dom.getAttribute(variableInput, 'data-original-value');
                        fireEvent(variableInput, 'change');
                    });
                } else {
                    Dom.setAttribute(variableInput, 'style', 'color: gray');
                    Dom.setAttribute(img, 'src', getImageFolder() + '/none.png');
                    Dom.setAttribute(img, 'style', '');
                    Event.removeListener(img, 'click');
                }
            }

            if (!_.contains(["com.elasticbox.jenkins.builders.UpdateOperation", "com.elasticbox.jenkins.builders.UpdateBox"],
                    Dom.getAttribute(descriptorElement, "descriptorid"))) {
                if (variable.required && !variableInput.value) {
                    Dom.addClass(div, "error");
                } else {
                    Dom.removeClass(div, "error");
                }
            }
        },

        createVariableRow = function (variable, savedVariable, variableHolder) {
            var saveVariable = function (name, value, scope, type, varTextBox, variableInput) {
                    var savedVariables = Dom.getAttribute(varTextBox, 'value').evalJSON(),
                        modified = type === 'Binding' ? value !== '' && value !== '()' :
                            value !== Dom.getAttribute(variableInput, 'data-original-value'),
                        modifiedVariable;

                    if (modified) {
                        modifiedVariable = _.findWhere( savedVariables, { name: name, scope: scope });
                        if (modifiedVariable) {
                            modifiedVariable.value = value;
                        } else {
                            savedVariables.push({ name: name, value: value, scope: scope, type: type });
                        }
                    } else {
                        savedVariables = _.reject(savedVariables, function (savedVar) {
                            return savedVar.name === name && savedVar.scope === scope;
                        });
                    }
                    Dom.setAttribute(varTextBox, 'value',  savedVariables.toJSON());
                },

                updateBindingOptions = function (currentValue, bindingSelect) {
                    var scope = Dom.getAttribute(bindingSelect, 'data-scope'),
                        deployBoxSteps = variableHolder.getPriorDeployBoxSteps(),
                        descriptorId = variableHolder.buildStepId,
                        tagsOption = Dom.getElementBy(function (option) {
                            return Dom.getAttribute(option, 'value') === '()';
                        }, 'option', bindingSelect),

                        selectedOption, noneOption, noneOptionText;

                    if (!currentValue) {
                        currentValue = bindingSelect.value;
                    }

                    if (variable.value !== 'AnyBox') {
                        deployBoxSteps = _.filter(deployBoxSteps, function (step) {
                            var boxSelect = _.first(Dom.getElementsByClassName('eb-box', 'select', step.element));
                            return boxSelect && boxSelect.value === variable.value;
                        });
                    }

                    noneOption = Dom.getElementBy(function (option) {
                        return Dom.getAttribute(option, 'value') === '';
                    }, 'option', bindingSelect);
                    if (noneOption) {
                        bindingSelect.removeChild(noneOption);
                        noneOptionText = noneOption.innerHTML;
                    }

                    if (tagsOption) {
                        bindingSelect.removeChild(tagsOption);
                    }

                    // remove existing options for deploy box steps
                    for (var child = Dom.getFirstChild(bindingSelect);
                            child !== null && ElasticBoxUtils.startsWith(child.getAttribute('value'), ElasticBoxUtils.DeployBoxDescriptorId);
                            child = Dom.getFirstChild(bindingSelect)) {
                        bindingSelect.removeChild(child);
                    }

                    bindingSelect.innerHTML = '<option value="()">Instances with tags</option>' +
                            _.map(deployBoxSteps, function (step) {
                                return ElasticBoxUtils.format('<option value="{0}">{1}</option>', step.id, step.name);
                            }).join(' ') + bindingSelect.innerHTML;
                    if (!noneOption) {
                        if (ElasticBoxUtils.startsWith(descriptorId, ElasticBoxUtils.DeployBoxDescriptorId)) {
                            noneOptionText = 'None';
                        } else {
                            // Reconfigure or Update operation
                            noneOptionText = 'Unchanged';
                        }
                    }
                    if (noneOptionText) {
                        bindingSelect.innerHTML = ElasticBoxUtils.format('<option value="">{0}</option>', noneOptionText) + bindingSelect.innerHTML;
                    }

                    selectedOption = Dom.getElementBy(function (option) {
                        return Dom.getAttribute(option, 'value') === currentValue;
                    }, 'option', bindingSelect);
                    if (!selectedOption) {
                        selectedOption = _.first(Dom.getChildren(bindingSelect));
                        saveVariable(variable.name, Dom.getAttribute(selectedOption, 'value'), scope, variable.type,
                            variableHolder.varTextBox, bindingSelect);
                    }
                    bindingSelect.selectedIndex = selectedOption ? Dom.getChildren(bindingSelect).indexOf(selectedOption) : 0;
                },

                toggleBindingTagsInput = function (bindingSelect) {
                    var tagsInput = Dom.getNextSibling(bindingSelect);

                    // Toggle tags input text box
                    if (bindingSelect.value === '()') {
                        Dom.setAttribute(tagsInput, 'style', '');
                        tagsInput.focus();
                    } else {
                        Dom.setAttribute(tagsInput, 'style', 'display: none;');
                    }
                },

                row = document.createElement('tr'),
                savedValue;

            variable.value = '';

            savedValue = savedVariable && savedVariable.value || variable.value;
            if (variable.type === 'Binding') {
                isBindingWithTags = savedValue.charAt(0) === '(' && savedValue.charAt(savedValue.length - 1) === ')';

                row.innerHTML = ElasticBoxUtils.format(BINDING_VARIABLE_TEMPLATE,
                    variable.name, '_' + variable.name, '()', variable.value, variable.scope,
                    '_' + variable.name + '_tags', savedValue.substr(1, savedValue.length - 2));
            } else {
                row.innerHTML = ElasticBoxUtils.format(TEXT_VARIABLE_TEMPLATE, variable.name, '_' + variable.name,
                    savedValue, variable.value, variable.scope, variable.type === 'Password' ? 'password' : 'text');
            }
            Dom.getElementsByClassName('eb-variable', variable.type === 'Binding' && 'select' || 'input', row, function (variableInput) {
                var savedValue = Dom.getAttribute(variableInput, 'value'),

                    instancesUrl, savedValue;

                Event.addListener(variableInput, 'change', function () {
                    saveVariable(variable.name, this.value, Dom.getAttribute(this, 'data-scope'), variable.type,
                        variableHolder.varTextBox, this);
                    toggleVariableDecorators(Dom.getAncestorByTagName(variableInput, 'tr'), variable);
                    if (variable.type === 'Binding') {
                        toggleBindingTagsInput(this);
                    }
                });
                if (variable.type === 'Binding') {
                    variableInput.innerHTML = '<option value="loading">Loading...</option>';

                    Event.addListener(variableInput, 'focus', function () {
                        updateBindingOptions(this.value, this);
                    });

                    Event.addListener(Dom.getNextSibling(variableInput), 'change', function () {
                        if (variableInput.value === '()') {
                            saveVariable(variable.name, ElasticBoxUtils.format('({0})', this.value),
                                Dom.getAttribute(variableInput, 'data-scope'), variable.type,
                                variableHolder.varTextBox, this);
                        }
                    });

                    variableInput.innerHTML = '';
                    updateBindingOptions(savedValue, variableInput);
                    toggleBindingTagsInput(variableInput);
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
                        Dom.setAttribute(row, 'style', expanded ? 'display: none;' : '');
                    }
                },

                varTableRows = [];

            _.each(boxes, function (box) {
                var variables = _.reject(box.variables, function (variable) {
                        return _.contains(['Box'], variable.type);
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
                            toggleVariableDecorators(row, variable);
                        }
                    });
                    varTableRows.push(varTableRow);
                }
            });

            _.each(_.rest(varTableRows), function (varTableRow) {
                toggleVarTable(varTableRow);
            });
        },

        removeInvalidVariables = function (variables, boxes) {
            var validVariables = _.flatten(_.pluck(boxes, 'variables'));
            return _.filter(variables, function (variable) {
                return _.findWhere(validVariables, {'name': variable.name, 'scope': variable.scope});
            });
        },

        refreshVariables = function (variableHolder) {
            var varHeader = _.first(Dom.getChildren(variableHolder.varTBody)),
                varTBodyElement = new Element(variableHolder.varTBody),
                boxStackUrl = getBoxStackUrl(variableHolder),

                clearVariables = function () {
                    _.each(_.rest(Dom.getChildren(variableHolder.varTBody)), function (row) {
                        varTBodyElement.removeChild(row);
                    });
                },

                messageRow = document.createElement('tr');

            Dom.addClass(varHeader, 'eb-header');

            if (!Dom.getAttribute(variableHolder.varTextBox, 'value')) {
                Dom.setAttribute(variableHolder.varTextBox, 'value', '[]');
            }

            if (boxStackUrl) {
                clearVariables();
                messageRow.innerHTML = '<td>Loading...</td>';
                variableHolder.varTBody.appendChild(messageRow);
                Connect.asyncRequest('GET', boxStackUrl, {
                    success: function (response) {
                        var boxes = response.responseText.evalJSON(),
                            savedVariablesJson = Dom.getAttribute(variableHolder.varTextBox, 'value'),
                            savedVariables = savedVariablesJson ? removeInvalidVariables(savedVariablesJson.evalJSON(), boxes) : [];

                        Dom.setAttribute(variableHolder.varTextBox, 'value', savedVariables.toJSON());

                        clearVariables();
                        addVariables(boxes, savedVariables, variableHolder);
                    },

                    failure: function (response) {
                        messageRow.innerHTML = ElasticBoxUtils.format('<td style="color: red;">Error {0}: {1}</td>', response.status, response.statusText);
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

            refreshVariables(variableHolder);
        },

        getVariableHolderInfo = function (type) {
            return {
                type: type,
                class: 'eb-' + type,
                changeListenerType: 'eb-' + type + '-change-listener'
            };
        },

        createVariableHolder = function (variableHolderSelect, variableHolderInfo, buildStepElement, varTBody, varTextBox) {
            var descriptorElement = ElasticBoxUtils.getDescriptorElement(variableHolderSelect);

            if (_.isUndefined(varTBody)) {
                varTBody = _.first(Dom.getElementsByClassName('eb-variable-inputs', 'tbody', descriptorElement));
            }
            if (_.isUndefined(varTextBox)) {
                varTextBox = _.first(Dom.getElementsByClassName('eb-variables', 'input', descriptorElement));
            }

            return {
                buildStepId: ElasticBoxUtils.getBuildStepId(buildStepElement),
                info: variableHolderInfo,
                varTBody: varTBody,
                varTextBox: varTextBox,
                select: variableHolderSelect,
                cloudSelect: _.first(Dom.getElementsByClassName('eb-cloud', 'select', buildStepElement)),
                workspaceSelect: _.first(Dom.getElementsByClassName('eb-workspace', 'select', buildStepElement)),
                boxSelect: _.first(Dom.getElementsByClassName('eb-box', 'select', descriptorElement)),
                profileSelect: _.first(Dom.getElementsByClassName('eb-profile', 'select', descriptorElement)),
                cloudFormationSelected: _.first(Dom.getElementsByClassName('eb-cloud-formation-selected', 'input', descriptorElement)),
                providerSelect: _.first(Dom.getElementsByClassName('eb-provider', 'select', descriptorElement)),
                locationSelect: _.first(Dom.getElementsByClassName('eb-location', 'select', descriptorElement)),
                getPriorDeployBoxSteps: function () {
                    var cloudName = this.cloudSelect ? this.cloudSelect.value : undefined;
                    return ElasticBoxUtils.getPriorDeployBoxSteps(buildStepElement, cloudName);
                }
            };
        },

        getBuildStepVariableHolders = function (buildStepElement, varTBody) {
            var _variableHolders = [],
                descriptorId = Dom.getAttribute(buildStepElement, 'descriptorid'),
                buildStepId = ElasticBoxUtils.getBuildStepId(buildStepElement),

                boxVersionSelects, variableHolderInfo, select, varTBodies, varTextBoxes;

            boxVersionSelects = Dom.getElementsByClassName('eb-boxVersion', 'select', buildStepElement);
            if (boxVersionSelects.length > 0) {
                _.each(boxVersionSelects, function (boxVersionSelect) {
                    _variableHolders.push(createVariableHolder(boxVersionSelect, getVariableHolderInfo('boxVersion'), buildStepElement));
                });
            } else {
                // TODO: remove this code after we remove obsolete build step Reconfigure Box and Reinstall Box
                variableHolderInfo = getVariableHolderInfo('instance');
                select = _.first(Dom.getElementsByClassName(variableHolderInfo.class, 'select', buildStepElement));
                varTBodies = Dom.getElementsByClassName('eb-variable-inputs', 'tbody', buildStepElement);
                varTextBoxes = Dom.getElementsByClassName('eb-variables', 'input', buildStepElement),
                _variableHolders.push(createVariableHolder(select, variableHolderInfo, buildStepElement, varTBody[0], varTextBoxes[0]));
                if (descriptorId === ElasticBoxUtils.ReconfigureBoxDescriptorId) {
                    _variableHolders.push({
                        buildStepId: buildStepId,
                        info: getVariableHolderInfo('buildstep'),
                        varTBody: varTBodies[1],
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
                            var name = Dom.getAttribute(element, 'name'),
                                descriptorId = Dom.getAttribute(element, 'descriptorid');
                            return  name === 'builder' && _.contains([
                                ElasticBoxUtils.DeployBoxDescriptorId,
                                ElasticBoxUtils.ReconfigureBoxDescriptorId,
                                ElasticBoxUtils.ManageInstanceDescriptorId,
                                ElasticBoxUtils.ManageBoxDescriptorId
                            ], descriptorId);
                        }),

                        variableHolder = {
                            varTBody: tbody,
                            getPriorDeployBoxSteps: function () { return []; }
                        },

                        descriptorElement;

                    if (variableHolderElement) {
                        _.each(getBuildStepVariableHolders(variableHolderElement, tbody), function (varHolder) {
                            if (!_.findWhere(_variableHolders, { select: varHolder.select })) {
                                _variableHolders.push(varHolder);
                            }
                        });
                    } else {
                        // variable holder is an InstanceCreator build wrapper or Slave Configuration
                        variableHolderElement = Dom.getAncestorByTagName(tbody, 'tr');
                        variableHolderElement = Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                            variableHolder.varTextBox = _.first(Dom.getElementsByClassName('eb-variables', 'input', element));
                            return !_.isUndefined(variableHolder.varTextBox);
                        });
                        if (variableHolder.varTextBox) {
                            Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                variableHolder.select = _.first(Dom.getElementsByClassName('eb-boxVersion', 'select', element));
                                return !_.isUndefined(variableHolder.select);
                            });
                            if (variableHolder.select) {
                                variableHolder.info = getVariableHolderInfo('boxVersion');
                            } else {
                                variableHolder.info = getVariableHolderInfo('profile');
                                variableHolderElement = Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                    variableHolder.select = _.first(Dom.getElementsByClassName(variableHolder.info.class, 'select', element));
                                    return !_.isUndefined(variableHolder.select);
                                });
                            }
                            descriptorElement = Dom.getAncestorBy(variableHolderElement, function (element) {
                                return Dom.getAttribute(element, 'descriptorid');
                            });
                            if (descriptorElement && Dom.getAttribute(descriptorElement, 'descriptorid') === ElasticBoxUtils.ElasticBoxCloudDescriptorId) {
                                variableHolder.endpointUrlInput = _.first(Dom.getElementsByClassName('eb-endpointUrl', 'input', descriptorElement));
                                variableHolder.usernameInput = _.first(Dom.getElementsByClassName('eb-username', 'input', descriptorElement));
                                variableHolder.passwordInput = _.first(Dom.getElementsByClassName('eb-password', 'input', descriptorElement));
                                variableHolder.tokenInput = _.first(Dom.getElementsByClassName('eb-token', 'input', descriptorElement));
                            } else {
                                Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                    variableHolder.cloudSelect = _.first(Dom.getElementsByClassName('eb-cloud', 'select', element));
                                    return !_.isUndefined(variableHolder.cloudSelect);
                                });
                            }
                            Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                variableHolder.workspaceSelect = _.first(Dom.getElementsByClassName('eb-workspace', 'select', element));
                                return !_.isUndefined(variableHolder.workspaceSelect);
                            });
                            Dom.getPreviousSiblingBy(variableHolderElement, function (element) {
                                variableHolder.boxSelect = _.first(Dom.getElementsByClassName('eb-box', 'select', element));
                                return !_.isUndefined(variableHolder.boxSelect);
                            });
                            Dom.getNextSiblingBy(variableHolderElement, function (element) {
                                variableHolder.profileSelect = _.first(Dom.getElementsByClassName('eb-profile', 'select', element));
                                return !_.isUndefined(variableHolder.profileSelect);
                            });
                            Dom.getNextSiblingBy(variableHolderElement, function (element) {
                                variableHolder.cloudFormationSelected = _.first(Dom.getElementsByClassName('eb-cloud-formation-selected', 'input', element));
                                return !_.isUndefined(variableHolder.cloudFormationSelected);
                            });
                            Dom.getNextSiblingBy(variableHolderElement, function (element) {
                                variableHolder.providerSelect = _.first(Dom.getElementsByClassName('eb-provider', 'select', element));
                                return !_.isUndefined(variableHolder.providerSelect);
                            });
                            Dom.getNextSiblingBy(variableHolderElement, function (element) {
                                variableHolder.locationSelect = _.first(Dom.getElementsByClassName('eb-location', 'select', element));
                                return !_.isUndefined(variableHolder.locationSelect);
                            });


                            _variableHolders.push(variableHolder);
                        }
                    }
                });

            return _variableHolders;
        },

    validateSavedOptions = function () {
        var itemsFilled = function (select) {
                var children = Dom.getChildren(select);

                if (children.length === 1 && _.first(children).innerHTML === Dom.getAttribute(select, 'value')) {
                    return false;
                }
                return true;
            },

            setValidationMessage = function (select, message) {
                var validationErrorArea = Dom.getNextSiblingBy(Dom.getAncestorByTagName(select, 'tr'), function (sibling) {
                        return Dom.hasClass(sibling, 'validation-error-area');
                    });

                if (validationErrorArea) {
                    validationErrorArea = _.last(Dom.getChildren(validationErrorArea));
                    validationErrorArea.innerHTML = message ? ElasticBoxUtils.format('<div class="error">{0}</div>', message) : '';
                    return true;
                }
                return false;
            },

            validate = function (select, errorMessage, dependencies) {
                if (!Dom.getAttribute(select, 'value')) {
                    return;
                }
                ElasticBoxUtils.waitUtil(function () {
                    return itemsFilled(select);
                },
                function () {
                    var savedValue = Dom.getAttribute(select, 'value'),
                        options = Dom.getChildren(select),
                        nextDependency = _.first(dependencies),
                        descriptorElement = ElasticBoxUtils.getDescriptorElement(select),
                        clearValidationMessage = function () {
                            var firstOption = Dom.getFirstChild(select);

                            if (firstOption && Dom.getAttribute(firstOption, 'value') === savedValue) {
                                select.removeChild(firstOption);
                            }
                            setValidationMessage(select);
                            Event.removeListener(select, 'change', clearValidationMessage);
                        },

                        invalidOption;

                    if (Dom.hasClass(select, 'ebx-validate')) {
                        return;
                    }

                    Dom.addClass(select, 'ebx-validate');
                    if (!Dom.getElementBy(function (option) {
                            return Dom.getAttribute(option, 'value') === savedValue;
                        }, 'option', select)) {
                        if (setValidationMessage(select, errorMessage)) {
                            if (options.length === 0) {
                                select.innerHTML = ElasticBoxUtils.format('<option value="{0}">{0}</option>', savedValue);
                            } else {
                                invalidOption = document.createElement('option');
                                invalidOption.innerHTML = savedValue;
                                Dom.setAttribute(invalidOption, 'value', savedValue);
                                Dom.insertBefore(invalidOption, Dom.getFirstChild(select));
                            }
                            select.value = savedValue;
                            setTimeout(function () {
                                fireEvent(select, 'change');
                                // add an event listener to 'select' element to clear the error message if new option is selected
                                Event.addListener(select, 'change', clearValidationMessage);
                            }, 1000);
                        }
                    } else if (_.isObject(nextDependency)) {
                        Dom.getElementsByClassName(nextDependency.class, 'select', descriptorElement,
                            function (dependentSelect) {
                                validate(dependentSelect, ElasticBoxUtils.format(nextDependency.errorMessageTemplate, Dom.getAttribute(dependentSelect, 'value')),
                                    _.rest(dependencies));
                            });
                    }
                }, 1000);
            };

        Dom.getElementsByClassName('eb-cloud', 'select', null, function (select) {
            validate(select, ElasticBoxUtils.format('The previously selected cloud {0} is not valid', Dom.getAttribute(select, 'value')),
                [
                    { class: 'eb-workspace', errorMessageTemplate: 'The previously selected workspace {0} is not available in the selected cloud' },
                    { class: 'eb-box', errorMessageTemplate: 'The previously selected box with id {0} is not available in the selected workspace' },
                    { class: 'eb-boxVersion', errorMessageTemplate: 'The previously selected box version with id {0} is not valid' },
                    { class: 'eb-profile', errorMessageTemplate: 'The previously selected profile with id {0} is not valid' }
                ]);
        });
    };

    return {
        initialize: function () {
            ElasticBoxUtils.initializeBuildSteps();
            validateSavedOptions();
            variableHolders = getVariableHolders();
            _.each(variableHolders, function (variableHolder) {
                toggleDeploymentOptions(variableHolder);
                populateVariables(variableHolder);
                addListeners(variableHolder);
            });
        }
    };

})();

(function() {
    setTimeout(ElasticBoxVariables.initialize, 500);
})();
