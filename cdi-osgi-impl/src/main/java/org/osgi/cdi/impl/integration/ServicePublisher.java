package org.osgi.cdi.impl.integration;

import org.osgi.cdi.impl.extension.CDIOSGiExtension;
import org.osgi.cdi.impl.extension.services.RegistrationsHolderImpl;
import org.osgi.cdi.api.extension.annotation.Publish;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceRegistration;

import javax.enterprise.inject.Instance;
import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 
 * @author Mathieu ANCELIN - SERLI (mathieu.ancelin@serli.com)
 */
public class ServicePublisher {

    private final Collection<String> classes;
    private final Bundle bundle;
    private final Instance<Object> instance;
    private final Set<String> blackList;

    public ServicePublisher(Collection<String> classes, Bundle bundle,
                            Instance<Object> instance, Set<String> blackList) {
        this.classes = classes;
        this.bundle = bundle;
        this.instance = instance;
        this.blackList = blackList;
    }

    public void registerAndLaunchComponents() {
        System.out.println(String.format("\nRegistering/Starting OSGi Service for bundle %s\n", bundle.getSymbolicName()));
        for (String className : classes) {
            Class<?> clazz = null;
            try {
                clazz = bundle.loadClass(className);
            } catch (Exception e) {
                //e.printStackTrace(); // silently ignore :-)
            }
            if (clazz != null) {
                boolean publishable = clazz.isAnnotationPresent(Publish.class);
                boolean instatiation = publishable;
                Annotation[] annotations = null;
                Object service = null;
                if (instatiation) {
                    List<Annotation> qualifiers = getQualifiers(clazz);
                    try {
                        annotations = qualifiers.toArray(new Annotation[qualifiers.size()]);
                        service = instance.select(clazz, annotations).get();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    if (publishable) {
                        publish(clazz, service, qualifiers);
                    }
                }
            }
        }
    }

    private void publish(Class<?> clazz, Object service, List<Annotation> qualifiers) {
        // register service
        Annotation[] annotations = qualifiers.toArray(new Annotation[qualifiers.size()]);
        ServiceRegistration registration = null;
        if (service != null) {
            Publish publish = clazz.getAnnotation(Publish.class);
            Class[] contracts = publish.contracts();
            Properties properties = getServiceProperties(publish, qualifiers);
            if (contracts.length != 0) {
                for (Class contract : contracts) {
                    System.out.println("Registering OSGi service " + clazz.getName() + " as " + contract.getName());
                    registration = bundle.getBundleContext().registerService(
                            contract.getName(), getProxy(contract, clazz, annotations, bundle), properties);
                }
            } else {
                // registering interfaces
                if (service.getClass().getInterfaces().length > 0) {
                    for (Class interf : service.getClass().getInterfaces()) {
                        if (!blackList.contains(interf.getName())) {
                            System.out.println("Registering OSGi service " + clazz.getName() + " as " + interf.getName());
                            registration = bundle.getBundleContext().registerService(
                                    interf.getName(), getProxy(interf, clazz, annotations, bundle), properties);
                        }
                    }
                } else {
                    System.out.println("Registering OSGi service " + clazz.getName() + " as " + clazz.getName());
                    registration = bundle.getBundleContext().registerService(clazz.getName(), service, properties);
                }
            }
        }
        if (registration != null) {
            CDIOSGiExtension.currentBundle.set(bundle.getBundleId());
            instance.select(RegistrationsHolderImpl.class).get().addRegistration(registration);
        }
    }

    private static Properties getServiceProperties(Publish publish, List<Annotation> qualifiers) {
        Properties properties = null;
        if (publish.useQualifiersAsProperties()) {
            if (!qualifiers.isEmpty()) {
                properties = new Properties();
                for (Annotation qualif : qualifiers) {
                    for (Method m : qualif.annotationType().getDeclaredMethods()) {
                        if (!m.isAnnotationPresent(Nonbinding.class)) {
                            try {
                                String key = qualif.annotationType().getName() + "." + m.getName();
                                Object value = m.invoke(qualif);
                                if (value == null) {
                                    value = m.getDefaultValue();
                                }
                                properties.setProperty(key, value.toString());
                            } catch (Throwable t) {
                                // ignore
                            }
                        }
                    }
                }
            }
        } else {
            if (publish.properties().length > 0) {
                properties = new Properties();
                for (String property : publish.properties()) {
                    if (property.split("=").length == 2) {
                        String key = property.split("=")[0];
                        String value = property.split("=")[1];
                        properties.setProperty(key, value);
                    }
                }
            }
        }
        return properties;
    }

    private static List<Annotation> getQualifiers(Class<?> clazz) {
        List<Annotation> qualifiers = new ArrayList<Annotation>();
        for (Annotation a : clazz.getAnnotations()) {
            if (a.annotationType().isAnnotationPresent(Qualifier.class)) {
                qualifiers.add(a);
            }
        }
        return qualifiers;
    }

    private <T> T getProxy(Class<T> interf, Class<? extends T> clazz, Annotation[] qualifiers, Bundle bundle) {
        return interf.cast(
                Proxy.newProxyInstance(
                    clazz.getClassLoader(),
                    new Class[]{interf},
                    new LazyService(clazz, qualifiers, bundle)));
    }

    private class LazyService implements InvocationHandler {

        private final Class<?> contract;
        private final Annotation[] qualifiers;
        private final Bundle bundle;

        public LazyService(Class<?> contract, Annotation[] qualifiers, Bundle bundle) {
            this.contract = contract;
            this.qualifiers = qualifiers;
            this.bundle = bundle;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                CDIOSGiExtension.currentBundle.set(bundle.getBundleId());
                return method.invoke(
                        instance.select(contract, qualifiers).get(),
                        args);
            } finally {
                CDIOSGiExtension.currentBundle.remove();
            }
        }
    }
}
