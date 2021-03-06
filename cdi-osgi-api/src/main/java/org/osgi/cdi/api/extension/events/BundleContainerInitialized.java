package org.osgi.cdi.api.extension.events;

import org.osgi.framework.BundleContext;

/**
 * @author Mathieu ANCELIN - SERLI (mathieu.ancelin@serli.com)
 * @author Matthieu CLOCHARD - SERLI (matthieu.clochard@serli.com)
 */
public class BundleContainerInitialized extends AbstractBundleContainerEvent {

    public BundleContainerInitialized(BundleContext context) {
        super(context);
    }

    @Override
    public BundleContainerEventType getType() {
        return BundleContainerEventType.INITIALIZED;
    }
}
