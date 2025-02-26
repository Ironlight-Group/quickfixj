/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved.
 *
 * This file is part of the QuickFIX FIX Engine
 *
 * This file may be distributed under the terms of the quickfixengine.org
 * license as defined by quickfixengine.org and appearing in the file
 * LICENSE included in the packaging of this file.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information.
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing
 * are not clear to you.
 ******************************************************************************/

package quickfix.examples.banzai;

import java.util.HashMap;
import java.util.Map;

public class OrderType {
    static private final Map<String, OrderType> known = new HashMap<>();
    static public final OrderType RFQ = new OrderType("Request For Quote");
    static public final OrderType LIMIT = new OrderType("Limit");
    static public final OrderType QUOTE = new OrderType("Quote");
    private final String name;

    static private final OrderType[] array = {LIMIT, RFQ, QUOTE};

    private OrderType(String name) {
        this.name = name;
        synchronized (OrderType.class) {
            known.put(name, this);
        }
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return name;
    }

    static public Object[] toArray() {
        return array;
    }

    public static OrderType parse(String type) throws IllegalArgumentException {
        OrderType result = known.get(type);
        if (result == null) {
            throw new IllegalArgumentException
            ("OrderType: " + type + " is unknown.");
        }
        return result;
    }
}
