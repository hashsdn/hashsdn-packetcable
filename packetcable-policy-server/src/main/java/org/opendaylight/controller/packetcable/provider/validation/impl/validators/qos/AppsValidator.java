/*
 * Copyright (c) 2015 CableLabs and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.packetcable.provider.validation.impl.validators.qos;

import org.opendaylight.controller.packetcable.provider.validation.ValidationException;
import org.opendaylight.controller.packetcable.provider.validation.impl.validators.AbstractValidator;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev151026.pcmm.qos.gates.Apps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev151026.pcmm.qos.gates.apps.App;

/**
 * @author rvail
 */
public class AppsValidator extends AbstractValidator<Apps> {

    private final AppValidator appValidator = new AppValidator();

    @Override
    public void validate(final Apps apps, final Extent extent) throws ValidationException {
        if (apps == null) {
            throw new ValidationException("apps must exist");
        }
        if (extent == Extent.NODE_AND_SUBTREE) {
            for (App app : apps.getApp()) {
                validateChild(appValidator, app);
            }
        }

        throwErrorsIfNeeded();
    }

}