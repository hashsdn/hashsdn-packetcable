/*
 * Copyright (c) 2015 CableLabs and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.packetcable.provider.validation.impl;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;
import org.opendaylight.controller.packetcable.provider.validation.ValidatorProvider;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.classifier.attributes.Classifiers;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.classifier.attributes.classifiers.ClassifierContainer;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.classifier.Classifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.ext.classifier.ExtClassifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.gate.spec.GateSpec;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.gates.Apps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.gates.apps.App;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.gates.apps.app.Subscribers;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.gates.apps.app.subscribers.Subscriber;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.gates.apps.app.subscribers.subscriber.Gates;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.gates.apps.app.subscribers.subscriber.gates.Gate;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.ipv6.classifier.Ipv6Classifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev161017.pcmm.qos.traffic.profile.TrafficProfile;

/**
 * @author rvail
 */
public class QosValidatorProviderFactoryTest {

    private final QosValidatorProviderFactory factory = new QosValidatorProviderFactory();

    @Test
    public void validBuild() {
        assertNotNull(factory.build());
        assertThat(factory.build(),  instanceOf(ValidatorProvider.class));

        assertNotNull(factory.build().validatorFor(Apps.class));
        assertNotNull(factory.build().validatorFor(App.class));
        assertNotNull(factory.build().validatorFor(Subscribers.class));
        assertNotNull(factory.build().validatorFor(Subscriber.class));
        assertNotNull(factory.build().validatorFor(Gates.class));
        assertNotNull(factory.build().validatorFor(Gate.class));
        assertNotNull(factory.build().validatorFor(GateSpec.class));
        assertNotNull(factory.build().validatorFor(TrafficProfile.class));
        assertNotNull(factory.build().validatorFor(Classifiers.class));
        assertNotNull(factory.build().validatorFor(Classifier.class));
        assertNotNull(factory.build().validatorFor(ClassifierContainer.class));
        assertNotNull(factory.build().validatorFor(ExtClassifier.class));
        assertNotNull(factory.build().validatorFor(Ipv6Classifier.class));

    }

}
