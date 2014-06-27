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

var ElasticBoxUtils = (function() {
    var DescriptorIdPrefix = 'com.elasticbox.jenkins.builders.',
        DeployBoxDescriptorId = DescriptorIdPrefix + 'DeployBox',
        DeployBoxBuildStepName = 'ElasticBox - Deploy Box',

        Dom = YAHOO.util.Dom,
        
        startsWith = function (str, prefix) {
            return str && str.substr(0, prefix.length) === prefix;
        };
        
    return {
        format: function () {
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
        
        startsWith: startsWith,
        
        uuid: function () {
            return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                var r = Math.random()*16|0, v = c === 'x' ? r : (r&0x3|0x8);
                return v.toString(16);
            });
        },

        DescriptorIdPrefix: DescriptorIdPrefix,
        DeployBoxDescriptorId: DeployBoxDescriptorId,
        DeployBoxBuildStepName: DeployBoxBuildStepName,
        
        getDeployBoxSteps: function (deployBoxStepElements) {
            if (!deployBoxStepElements) {
                deployBoxStepElements = Dom.getElementsBy(function (element) {
                    return Dom.getAttribute(element, 'descriptorid') === DeployBoxDescriptorId;
                }, 'div', document);
            }

            return _.map(deployBoxStepElements, function (step) {
                return {
                    id: Dom.getAttribute(_.first(Dom.getElementsByClassName('eb-id', 'input', step)), 'value'),
                    name: Dom.getElementBy(function (element) {
                        return startsWith(element.innerHTML, DeployBoxBuildStepName);
                    }, null, step).innerHTML
                };
            });
        },
        
        getBuildStepId: function (buildStep) {
            var idInput = _.first(Dom.getElementsByClassName('eb-id', 'input', buildStep));
            return idInput ? idInput.value : null;
        }
    };
})();
