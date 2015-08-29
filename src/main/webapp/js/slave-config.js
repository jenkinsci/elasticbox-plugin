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

        addListeners = function () {
            Dom.getElementsBy(function (element) {
                return Dom.getAttribute(element, 'descriptorid') === 'com.elasticbox.jenkins.ElasticBoxCloud';
            }, 'div', document,
            function (cloudElement) {
                Dom.getElementsByClassName('repeatable-add', 'span', cloudElement, function (addSlaveConfigSpan) {
                    Event.addListener(addSlaveConfigSpan, 'click', function () {
                        setTimeout(ElasticBoxVariables.initialize, 500);
                    });
                });
            });
        };

    setTimeout(addListeners, 500);

})();
