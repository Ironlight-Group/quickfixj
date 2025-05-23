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

import quickfix.SessionID;

public class Order implements Cloneable {
    private SessionID sessionID = null;
    private String symbol = null;
    private int quantity = 0;
    private int open = 0;
    private double executed = 0;
    private OrderSide side = null;
    private OrderType type = null;
    private OrderTIF tif = null;
    private Double limit = null;
    private double avgPx = 0.0;
    private boolean rejected = false;
    private boolean canceled = false;
    private boolean isNew = true;
    private String message = null;
    private String ID = null;
    private String originalID = null;
    private static int nextID = 1;
    private String quoteReqID = null;
    private String quoteID = null;
    private int quoteRespType = 0;
    private String quoteRespID = null;
    private boolean interactable = true;

    public Order() {
        ID = generateID();
    }

    public Order(String ID) {
        this.ID = ID;
    }

    public Object clone() {
        try {
            Order order = (Order) super.clone();
            order.setOriginalID(getID());
            order.setID(order.generateID());
            return order;
        } catch (CloneNotSupportedException e) {}
        return null;
    }

    //Use this for cases where an ID simply needs to be unique
    public String generateID() {
        return Long.toString(System.currentTimeMillis() + (nextID++));
    }

    public SessionID getSessionID() {
        return sessionID;
    }

    public void setSessionID(SessionID sessionID) {
        this.sessionID = sessionID;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getOpen() {
        return open;
    }

    public void setOpen(int open) {
        this.open = open;
    }

    public double getExecuted() {
        return executed;
    }

    public void setExecuted(double executed) { this.executed = executed; }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public OrderTIF getTIF() {
        return tif;
    }

    public void setTIF(OrderTIF tif) {
        this.tif = tif;
    }

    public Double getLimit() {
        return limit;
    }

    public void setLimit(Double limit) {
        this.limit = limit;
    }

    public void setLimit(String limit) {
        if (limit == null || limit.equals("")) {
            this.limit = null;
        } else {
            this.limit = Double.parseDouble(limit);
        }
    }

    public void setAvgPx(double avgPx) {
        this.avgPx = avgPx;
    }

    public double getAvgPx() {
        return avgPx;
    }

    public void setRejected(boolean rejected) {
        this.rejected = rejected;
    }

    public boolean getRejected() {
        return rejected;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean getCanceled() {
        return canceled;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getID() {
        return ID;
    }

    public void setOriginalID(String originalID) {
        this.originalID = originalID;
    }

    public String getOriginalID() {
        return originalID;
    }

    public void setQuoteReqID(String quoteReqID) {
        this.quoteReqID = quoteReqID;
    }

    public String getQuoteReqID() {
        return quoteReqID;
    }

    public void setQuoteID(String quoteID) {
        this.quoteID = quoteID;
    }

    public String getQuoteID() {
        return quoteID;
    }

    public void setQuoteRespType(int quoteRespType){
        this.quoteRespType = quoteRespType;
    }

    public int getQuoteRespType() {
        return quoteRespType;
    }

    public void setQuoteRespID(int quoteRespType){
        this.quoteRespID = quoteRespID;
    }

    public String getQuoteRespID() {
        return quoteRespID;
    }

    public boolean getInteractable() {
        return interactable;
    }

    public void updateInteractable() {
        this.interactable = (type == OrderType.LIMIT);
    }
}
