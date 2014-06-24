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
    var format = function () {
            var args = Array.prototype.slice.call(arguments),
                result = args.shift(),
                i;

            if (result) {
                for (i = 0; i < args.length; i++) {
                    result = result.split('{' + i + '}').join(args[i]);
                }
            }

            return result;
        },

        TEXT_VARIABLE_TEMPLATE = '<tr><td class="setting-leftspace">&nbsp;</td><td class="setting-name">{0}</td>' + 
                '<td class="setting-main"><input name="{0}" value="{1}" data-original-value="{2}" class="setting-input eb-variable" type="text"></td>' +
                '<td>&nbsp;</td></tr>',

        Dom = YAHOO.util.Dom,
        Element = YAHOO.util.Element,
        Event = YAHOO.util.Event,
        Connect = YAHOO.util.Connect,

        createVariableRow = function (variable, savedVariable, varTextBox) {
            var row = document.createElement('tr');

            if (variable.type === 'Text') {
                row.innerHTML = format(TEXT_VARIABLE_TEMPLATE, variable.name, savedVariable && savedVariable.value || variable.value, variable.value);
                Dom.getElementsByClassName('eb-variable', 'input', row, function (variableInput) {
                    Event.addListener(variableInput, 'change', function (event) {
                        var name = Dom.getAttribute(this, 'name'),
                            value = this.value,
                            boxVariables = Dom.getAttribute(varTextBox, 'value').evalJSON(),
                            modifiedVariable;

                        if (value === Dom.getAttribute(this, 'data-original-value')) {
                            boxVariables = _.filter(boxVariables, function (variable) {
                                return variable.name === name;
                            });
                        } else {
                            modifiedVariable = _.findWhere(boxVariables, { name: name });
                            if (modifiedVariable) {
                                modifiedVariable.value = value;
                            } else {
                                boxVariables.push({ name: name, value: value });
                            }
                        }
                        
                        Dom.setAttribute(varTextBox, 'value', boxVariables.toJSON());
                    });
                });
            }

            return row;
        },
        
        refreshVariables = function (variableHolderSelect, varTBody, varTextBox, variableHolderType) {
            var varHeader = _.first(Dom.getChildren(varTBody)),
                varTBodyElement = new Element(varTBody),
                fillUrl = Dom.getAttribute(variableHolderSelect, 'fillurl'),
                variableHolderId = variableHolderSelect.value,
                varUrl;
            
            Dom.addClass(varHeader, 'eb-header');
            if (!Dom.getAttribute(varTextBox, 'value')) {
                Dom.setAttribute(varTextBox, 'value', '[]');
            }

            _.each(_.rest(Dom.getChildren(varTBody)), function (row) {
                varTBodyElement.removeChild(row);
            });

            if (variableHolderId) {
                varUrl = format('{0}/getVariables?{1}={2}', fillUrl.substring(0, fillUrl.lastIndexOf('/')), variableHolderType, variableHolderId); 
                Connect.asyncRequest('GET', varUrl, {
                    success: function (response) {
                        var variables = response.responseText.evalJSON();
                        _.each(variables, function (variable) {
                            var row = createVariableRow(variable, null, varTextBox);
                            if (row) {
                                varTBodyElement.appendChild(row);
                            }
                        });
                    },

                    failure: function (response) {

                    }
                });
            }            
        },
        
        populateVariables = function (variableHolderSelect, varTBody, varTextBox, variableHolderType) {
            var varTBodyElement = new Element(varTBody),                        
                varHeader = _.first(Dom.getChildren(varTBody)),                
                fillUrl = Dom.getAttribute(variableHolderSelect, 'fillurl'),
                variableHolderId = variableHolderSelect.value,
                varUrl;
            
            
            if (!variableHolderId || Dom.hasClass(varHeader, 'eb-header')) {
                return;
            }
            Dom.addClass(varHeader, 'eb-header');
            if (!Dom.getAttribute(varTextBox, 'value')) {
                Dom.setAttribute(varTextBox, 'value', '[]');
            }
            varUrl = format('{0}/getVariables?{1}={2}', fillUrl.substring(0, fillUrl.lastIndexOf('/')), variableHolderType, variableHolderId); 
            Connect.asyncRequest('GET', varUrl, {
                success: function (response) {
                    var savedVariables = Dom.getAttribute(varTextBox, 'value').evalJSON(),
                        variables = response.responseText.evalJSON(),
                        savedVariable;                    
                    
                    Dom.setAttribute(varTextBox, 'data-variables', response.responseText);
                    _.each(variables, function (variable) {
                        savedVariable = _.findWhere(savedVariables, { name: variable.name });
                        var row = createVariableRow(variable, savedVariable, varTextBox);
                        if (row) {
                            varTBodyElement.appendChild(row);
                        }
                    });
                },

                failure: function (response) {
                    //TODO: report error
                }
            });
            
        }
        
        initialize = function () {
            Dom.getElementsByClassName('eb-variable-inputs', 'tbody', document, 
                function (tbody) {
                    var builder = Dom.getAncestorBy(tbody, function (element) {
                            var descriptorId = Dom.getAttribute(element, 'descriptorid');
                            return  descriptorId === 'com.elasticbox.jenkins.builders.LaunchBox' || 
                                    descriptorId === 'com.elasticbox.jenkins.builders.ReconfigureBox';
                        }),
                        builderElement = new Element(builder),                    
                        varTBody = _.first(builderElement.getElementsByClassName('eb-variable-inputs', 'tbody')),
                        varTextBox = _.first(builderElement.getElementsByClassName('eb-variables', 'input')),
                        
                        variableHolderType = Dom.getAttribute(builder, 'descriptorid') === 'com.elasticbox.jenkins.builders.LaunchBox' && 'profile' || 'instance',
                        variableHolderClass = 'eb-' + variableHolderType,
                        variableHolderChangeListenerType = 'eb-' + variableHolderType + '-change-listener';

                    if (varTBody && varTextBox) {
                        Dom.getElementsByClassName(variableHolderClass, 'select', builder, function (element) {
                            populateVariables(element, varTBody, varTextBox, variableHolderType);
                            
                            if (!_.some(Event.getListeners(element, 'change'), function (listener) {
                                return listener.obj === variableHolderChangeListenerType;
                            })) {
                                Event.addListener(element, 'change', function () {
                                    refreshVariables(this, varTBody, varTextBox, variableHolderType);
                                }, variableHolderChangeListenerType);

                            }                            
                        });
                    }
                            

                });            
        };

    setTimeout(initialize, 1000);

})();
