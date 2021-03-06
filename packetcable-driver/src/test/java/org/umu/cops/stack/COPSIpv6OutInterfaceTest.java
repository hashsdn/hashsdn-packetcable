package org.umu.cops.stack;

import org.junit.Assert;
import org.junit.Test;
import org.umu.cops.stack.COPSObjHeader.CNum;
import org.umu.cops.stack.COPSObjHeader.CType;

import java.io.ByteArrayOutputStream;

/**
 * Tests for the first constructor of the COPSIpv6OutInterfaceTest class.
 * Should any of these tests be inaccurate it is due to the fact that they have been written after COPSAcctTimer had been
 * released and my assumptions may be incorrect.
 */
public class COPSIpv6OutInterfaceTest {

    @Test(expected = IllegalArgumentException.class)
    public void nullAddress() {
        new COPSIpv6OutInterface(null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullHeader() throws Exception {
        new COPSIpv6OutInterface(null, new COPSIpv6Address("localhost"), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullAddressWithHeader() {
        new COPSIpv6OutInterface(new COPSObjHeader(CNum.OUTINTF, CType.STATELESS), null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCNum() {
        new COPSIpv6OutInterface(new COPSObjHeader(CNum.HANDLE, CType.STATELESS), null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCType() {
        new COPSIpv6OutInterface(new COPSObjHeader(CNum.OUTINTF, CType.DEF), null, 0);
    }

    @Test
    public void valid() throws Exception {
        final COPSIpv6Address address = new COPSIpv6Address("localhost");
        final COPSIpv6OutInterface intf = new COPSIpv6OutInterface(address, 5);
        Assert.assertEquals(new COPSObjHeader(CNum.OUTINTF, CType.STATELESS), intf.getHeader());
        Assert.assertEquals(20, intf.getDataLength());
        Assert.assertEquals(address, intf._addr);
        Assert.assertEquals(5, intf._ifindex);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        intf.dump(os);

        final String out = new String(os.toByteArray());
        System.out.println(out);
        final String[] lines = out.split("\n");
        Assert.assertEquals(5, lines.length);
        Assert.assertEquals("**Out-Interface**", lines[0]);
        Assert.assertEquals("C-num: OUTINTF", lines[1]);
        Assert.assertEquals("C-type: STATELESS", lines[2]);
        Assert.assertTrue(lines[3].equals("Address: localhost") || lines[3].equals("Address: 0:0:0:0:0:0:0:1"));
        Assert.assertEquals("ifindex: 5", lines[4]);
    }

    // The writeData() method will be tested implicitly via any of the COPSMsg tests
}
