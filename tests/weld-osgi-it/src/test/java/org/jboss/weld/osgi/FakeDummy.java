/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jboss.weld.osgi;

import com.sample.osgi.api.DummyService;

/**
 *
 * @author mathieuancelin
 */
public class FakeDummy implements DummyService {

    @Override
    public int times(int base, int time) {
        return base * (time*2);
    }
}
