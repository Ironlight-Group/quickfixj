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

import quickfix.Application;
import quickfix.DefaultMessageFactory;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.FixVersions;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.UnsupportedMessageType;
import quickfix.ConfigError;
import quickfix.Group;
import quickfix.field.AvgPx;
import quickfix.field.BidPx;
import quickfix.field.OfferPx;
import quickfix.field.BeginString;
import quickfix.field.BusinessRejectReason;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.CxlType;
import quickfix.field.DeliverToCompID;
import quickfix.field.ExecID;
import quickfix.field.HandlInst;
import quickfix.field.LastPx;
import quickfix.field.LastShares;
import quickfix.field.LeavesQty;
import quickfix.field.LocateReqd;
import quickfix.field.MsgSeqNum;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.QuoteReqID;
import quickfix.field.QuoteID;
import quickfix.field.QuoteType;
import quickfix.field.Price;
import quickfix.field.RefMsgType;
import quickfix.field.RefSeqNum;
import quickfix.field.SenderCompID;
import quickfix.field.SessionRejectReason;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TargetCompID;
import quickfix.field.Text;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;
import quickfix.field.BidSize;
import quickfix.field.OfferSize;
import quickfix.field.QuoteRespID;
import quickfix.field.QuoteRespType;
import quickfix.field.ExecType;
import quickfix.fix44.component.Instrument;
import quickfix.fix44.component.OrderQtyData;
import quickfix.fix44.QuoteRequestReject;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class BanzaiApplication implements Application {
    private final DefaultMessageFactory messageFactory = new DefaultMessageFactory();
    private OrderTableModel orderTableModel = null;
    private ExecutionTableModel executionTableModel = null;
    private SessionSettings settings = null;
    private final ObservableOrder observableOrder = new ObservableOrder();
    private final ObservableLogon observableLogon = new ObservableLogon();
    private boolean isAvailable = true;
    private boolean isMissingField;

    static private final TwoWayMap sideMap = new TwoWayMap();
    static private final TwoWayMap typeMap = new TwoWayMap();
    static private final TwoWayMap tifMap = new TwoWayMap();
    static private final HashMap<SessionID, HashSet<ExecID>> execIDs = new HashMap<>();

    public BanzaiApplication(OrderTableModel orderTableModel,
            ExecutionTableModel executionTableModel,
            SessionSettings settings) {
        this.orderTableModel = orderTableModel;
        this.executionTableModel = executionTableModel;
        this.settings = settings;
    }

    public void onCreate(SessionID sessionID) {
        try {
            //delete all previous session data before connecting to prevent e.g. sequence num errors
            String senderCompId = null;
            senderCompId = settings.getString(SessionSettings.SENDERCOMPID);
            String path = "target/data/banzai/FIX.4.4-" + senderCompId + "-IRON";
            File[] sessionData = {new File(path+".body"),new File(path+".header"),new File(path+".senderseqnums"),new File(path+".session"),new File(path+".targetseqnums")}; 
            for (File file : sessionData){
                if (file.delete()) { 
                      System.out.println("Deleted the file: " + file.getName());
                    } else {
                      System.out.println("Failed to delete some session data. Could it be missing?");
                    } 
            }
        } catch (ConfigError e) {
            System.out.println("ConfigError ocurred: " + e);
        }
    }

    public void onLogon(SessionID sessionID) {
        observableLogon.logon(sessionID);
    }

    public void onLogout(SessionID sessionID) {
        observableLogon.logoff(sessionID);
    }

    public void toAdmin(quickfix.Message message, SessionID sessionID) {
    }

    public void toApp(quickfix.Message message, SessionID sessionID) throws DoNotSend {
    }

    public void fromAdmin(quickfix.Message message, SessionID sessionID) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    }

    public void fromApp(quickfix.Message message, SessionID sessionID) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        try {
            SwingUtilities.invokeLater(new MessageProcessor(message, sessionID));
        } catch (Exception e) {
        }
    }

    //Entry point for messages sent to the client
    public class MessageProcessor implements Runnable {
        private final quickfix.Message message;
        private final SessionID sessionID;

        public MessageProcessor(quickfix.Message message, SessionID sessionID) {
            this.message = message;
            this.sessionID = sessionID;
        }

        public void run() {
            try {
                MsgType msgType = new MsgType();
                if (isAvailable) {
                    if (isMissingField) {
                        // For OpenFIX certification testing
                        sendBusinessReject(message, BusinessRejectReason.CONDITIONALLY_REQUIRED_FIELD_MISSING, "Conditionally required field missing");
                    }
                    else if (message.getHeader().isSetField(DeliverToCompID.FIELD)) {
                        // This is here to support OpenFIX certification
                        sendSessionReject(message, SessionRejectReason.COMPID_PROBLEM);
                    } else if (message.getHeader().getField(msgType).valueEquals(MsgType.EXECUTION_REPORT)) {
                        executionReport(message, sessionID);
                    } else if (message.getHeader().getField(msgType).valueEquals(MsgType.ORDER_CANCEL_REJECT)) {
                        cancelReject(message, sessionID);
                    } else if (message.getHeader().getField(msgType).valueEquals(MsgType.QUOTE_STATUS_REPORT)) {
                        System.out.println("Got quote status report");
                    } else if (message.getHeader().getField(msgType).valueEquals(MsgType.QUOTE_REQUEST_REJECT)) {
                        System.out.println("Got quote reject");
                    } else if (message.getHeader().getField(msgType).valueEquals(MsgType.QUOTE_RESPONSE)) {
                        System.out.println("Got quote response");
                    } else if (message.getHeader().getField(msgType).valueEquals(MsgType.QUOTE_REQUEST)) {
                        System.out.println(message.toString());
                        Order quoteRequest = new Order();
                        Group noRelatedSymGroup = new quickfix.fix44.QuoteRequest.NoRelatedSym();
                        Group group = message.getGroup(1, noRelatedSymGroup);
                        quoteRequest.setSymbol(group.getString(Symbol.FIELD));
                        quoteRequest.setQuoteReqID(message.getString(QuoteReqID.FIELD));
                        quoteRequest.setQuantity(group.getInt(OrderQty.FIELD));
                        quoteRequest.setType(OrderType.RFQ);
                        quoteRequest.setOpen(quoteRequest.getQuantity());
                        if (group.isSetField(Side.FIELD)) {
                            String side = group.getString(Side.FIELD);
                            if (side.equals("1")) {
                                quoteRequest.setSide(OrderSide.BUY);
                            } else if (side.equals("2")) {
                                quoteRequest.setSide(OrderSide.SELL);
                            }
                        }
                        orderTableModel.addOrder(quoteRequest);

                    } else if (message.getHeader().getField(msgType).valueEquals(MsgType.QUOTE)) {

                        if (!message.isSetField(QuoteReqID.FIELD)) {
                            System.err.println("Received Quote message without QuoteReqID (Tag 131). Cannot link to original request.");
                            quickfix.fix44.QuoteRequestReject reject = new quickfix.fix44.QuoteRequestReject();
                            reject.set(new QuoteReqID("N/A"));
                            reject.set(new Text("Missing QuoteReqID (Tag 131)"));
                            Session.sendToTarget(reject, sessionID);
                            return;
                        }

                        String quoteReqID = message.getString(QuoteReqID.FIELD);

                        Order originalRequestOrder = orderTableModel.getOrder(quoteReqID);
                        if (originalRequestOrder == null) {
                            System.err.println("Received Quote for an unknown or previously cleared QuoteReqID: " + quoteReqID);
                            return;
                        }

                        Order incomingQuoteOrder = new Order();
                        incomingQuoteOrder.setSymbol(message.getString(Symbol.FIELD));
                        incomingQuoteOrder.setQuoteID(message.getString(QuoteID.FIELD));

                        // Determine the side of the incoming quote
                        if (message.isSetField(BidPx.FIELD)) { // BUY
                            incomingQuoteOrder.setQuantity(message.getInt(BidSize.FIELD));
                            incomingQuoteOrder.setLimit(message.getDouble(BidPx.FIELD));
                            incomingQuoteOrder.setSide(OrderSide.BUY);
                        } else if (message.isSetField(OfferPx.FIELD)) { // SELL
                            incomingQuoteOrder.setQuantity(message.getInt(OfferSize.FIELD));
                            incomingQuoteOrder.setLimit(message.getDouble(OfferPx.FIELD));
                            incomingQuoteOrder.setSide(OrderSide.SELL);
                        }
                        incomingQuoteOrder.setType(OrderType.QUOTE);
                        incomingQuoteOrder.setOpen(incomingQuoteOrder.getQuantity());
                        incomingQuoteOrder.setSessionID(sessionID); // Associate session

                        OrderSide requestSide = originalRequestOrder.getSide();
                        OrderSide quoteSide = incomingQuoteOrder.getSide();

                        boolean addQuoteToTable = false;

                        if (requestSide == null || requestSide == OrderSide.UNDISCLOSED) {
                            // If the request did not specify a side, add the quote unconditionally
                            addQuoteToTable = true;
                        } else {
                            // If the request specified a side, check if the quote side is opposite
                            addQuoteToTable = (requestSide == OrderSide.BUY && quoteSide == OrderSide.SELL) ||
                                              (requestSide == OrderSide.SELL && quoteSide == OrderSide.BUY);
                        }

                        if (addQuoteToTable) {
                            orderTableModel.addOrder(incomingQuoteOrder);
                        }
                    } else {
                        sendBusinessReject(message, BusinessRejectReason.UNSUPPORTED_MESSAGE_TYPE,
                                "Unsupported Message Type");
                    }
                } else {
                    sendBusinessReject(message, BusinessRejectReason.APPLICATION_NOT_AVAILABLE,
                            "Application not available");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendSessionReject(Message message, int rejectReason) throws FieldNotFound,
            SessionNotFound {
        Message reply = createMessage(message, MsgType.REJECT);
        reverseRoute(message, reply);
        String refSeqNum = message.getHeader().getString(MsgSeqNum.FIELD);
        reply.setString(RefSeqNum.FIELD, refSeqNum);
        reply.setString(RefMsgType.FIELD, message.getHeader().getString(MsgType.FIELD));
        reply.setInt(SessionRejectReason.FIELD, rejectReason);
        Session.sendToTarget(reply);
    }

    private void sendBusinessReject(Message message, int rejectReason, String rejectText)
            throws FieldNotFound, SessionNotFound {
        Message reply = createMessage(message, MsgType.BUSINESS_MESSAGE_REJECT);
        reverseRoute(message, reply);
        String refSeqNum = message.getHeader().getString(MsgSeqNum.FIELD);
        reply.setString(RefSeqNum.FIELD, refSeqNum);
        reply.setString(RefMsgType.FIELD, message.getHeader().getString(MsgType.FIELD));
        reply.setInt(BusinessRejectReason.FIELD, rejectReason);
        reply.setString(Text.FIELD, rejectText);
        Session.sendToTarget(reply);
    }

    private Message createMessage(Message message, String msgType) throws FieldNotFound {
        return messageFactory.create(message.getHeader().getString(BeginString.FIELD), msgType);
    }

    private void reverseRoute(Message message, Message reply) throws FieldNotFound {
        reply.getHeader().setString(SenderCompID.FIELD,
                message.getHeader().getString(TargetCompID.FIELD));
        reply.getHeader().setString(TargetCompID.FIELD,
                message.getHeader().getString(SenderCompID.FIELD));
    }

    private void executionReport(Message message, SessionID sessionID) throws FieldNotFound {

        ExecID execID = (ExecID) message.getField(new ExecID());
        if (alreadyProcessed(execID, sessionID))
            return;

        Order order = orderTableModel.getOrder(message.getField(new ClOrdID()).getValue());
        if (order == null) {
            return;
        }

        BigDecimal fillSize;

        if (message.isSetField(LastShares.FIELD)) {
            LastShares lastShares = new LastShares();
            message.getField(lastShares);
            fillSize = new BigDecimal(lastShares.getValue());
        } else {
            // > FIX 4.1
            LeavesQty leavesQty = new LeavesQty();
            message.getField(leavesQty);
            fillSize = new BigDecimal(order.getQuantity()).subtract(new BigDecimal(leavesQty.getValue()));
        }


        BigDecimal cumQty = BigDecimal.ZERO;
        BigDecimal leavesQty = BigDecimal.ZERO;

        if (message.isSetField(CumQty.FIELD)) {
            cumQty = new BigDecimal(message.getString(CumQty.FIELD));
        }

        if (message.isSetField(LeavesQty.FIELD)) {
            leavesQty = new BigDecimal(message.getString(LeavesQty.FIELD));
        }

        ExecType execType = new ExecType();

        if (message.getField(execType).getValue() == ExecType.REPLACED) {
            //handle cancel replace
            BigDecimal newTotalQuantity = cumQty.add(leavesQty);
            order.setQuantity(newTotalQuantity.intValue());
            order.setOpen(leavesQty.intValue());
            order.setLimit(message.getString(Price.FIELD));
        } else if (fillSize.compareTo(BigDecimal.ZERO) > 0) {
            //handle other fills
            order.setOpen(order.getOpen() - (int) Double.parseDouble(fillSize.toPlainString()));
            order.setExecuted(Double.parseDouble(message.getString(CumQty.FIELD)));
            order.setAvgPx(Double.parseDouble(message.getString(AvgPx.FIELD)));
        }

        OrdStatus ordStatus = (OrdStatus) message.getField(new OrdStatus());

        if (ordStatus.valueEquals(OrdStatus.REJECTED)) {
            order.setRejected(true);
            order.setOpen(0);
        } else if (ordStatus.valueEquals(OrdStatus.CANCELED)
                || ordStatus.valueEquals(OrdStatus.DONE_FOR_DAY)) {
            order.setCanceled(true);
            order.setOpen(0);
        } else if (ordStatus.valueEquals(OrdStatus.NEW)) {
            if (order.isNew()) {
                order.setNew(false);
            }
        }

        try {
            order.setMessage(message.getField(new Text()).getValue());
        } catch (FieldNotFound e) {
        }

        orderTableModel.updateOrder(order, message.getField(new ClOrdID()).getValue());
        observableOrder.update(order);

        if (fillSize.compareTo(BigDecimal.ZERO) > 0 && message.getField(execType).getValue() != ExecType.REPLACED) {
            Execution execution = new Execution();
            execution.setExchangeID(sessionID + message.getField(new ExecID()).getValue());

            execution.setSymbol(message.getField(new Symbol()).getValue());
            execution.setQuantity(fillSize.intValue());
            if (message.isSetField(LastPx.FIELD)) {
                execution.setPrice(Double.parseDouble(message.getString(LastPx.FIELD)));
            }
            Side side = (Side) message.getField(new Side());
            execution.setSide(FIXSideToSide(side));
            executionTableModel.addExecution(execution);
        }
    }

    private void cancelReject(Message message, SessionID sessionID) throws FieldNotFound {

        String id = message.getField(new ClOrdID()).getValue();
        Order order = orderTableModel.getOrder(id);
        if (order == null)
            return;
        if (order.getOriginalID() != null)
            order = orderTableModel.getOrder(order.getOriginalID());

        try {
            order.setMessage(message.getField(new Text()).getValue());
        } catch (FieldNotFound e) {
        }
        orderTableModel.updateOrder(order, message.getField(new OrigClOrdID()).getValue());
    }

    private boolean alreadyProcessed(ExecID execID, SessionID sessionID) {
        HashSet<ExecID> set = execIDs.get(sessionID);
        if (set == null) {
            set = new HashSet<>();
            set.add(execID);
            execIDs.put(sessionID, set);
            return false;
        } else {
            if (set.contains(execID))
                return true;
            set.add(execID);
            return false;
        }
    }

    private void send(quickfix.Message message, SessionID sessionID) {
        try {
            Session.sendToTarget(message, sessionID);
        } catch (SessionNotFound e) {
            System.out.println(e);
        }
    }

    public void send(Order order) {
        String beginString = order.getSessionID().getBeginString();
        switch (beginString) {
            case FixVersions.BEGINSTRING_FIX40:
                send40(order);
                break;
            case FixVersions.BEGINSTRING_FIX41:
                send41(order);
                break;
            case FixVersions.BEGINSTRING_FIX42:
                send42(order);
                break;
            case FixVersions.BEGINSTRING_FIX43:
                send43(order);
                break;
            case FixVersions.BEGINSTRING_FIX44:
                send44(order);
                break;
            case FixVersions.BEGINSTRING_FIXT11:
                send50(order);
                break;
        }
    }

    public void send40(Order order) {
        quickfix.fix40.NewOrderSingle newOrderSingle = new quickfix.fix40.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), new OrderQty(order.getQuantity()),
                typeToFIXType(order.getType()));

        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send41(Order order) {
        quickfix.fix41.NewOrderSingle newOrderSingle = new quickfix.fix41.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity()));

        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send42(Order order) {
        quickfix.fix42.NewOrderSingle newOrderSingle = new quickfix.fix42.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), new TransactTime(), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity()));

        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send43(Order order) {
        quickfix.fix43.NewOrderSingle newOrderSingle = new quickfix.fix43.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), sideToFIXSide(order.getSide()),
                new TransactTime(), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity()));
        newOrderSingle.set(new Symbol(order.getSymbol()));
        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send44(Order order) {
        order.updateInteractable();
        if (order.getType() == OrderType.RFQ) {
            quickfix.fix44.QuoteRequest quoteRequest = new quickfix.fix44.QuoteRequest(new QuoteReqID(order.getID()));
            quickfix.fix44.QuoteRequest.NoRelatedSym noRelatedSym = new quickfix.fix44.QuoteRequest.NoRelatedSym();

            if (order.getSide() == OrderSide.BUY || order.getSide() == OrderSide.SELL) {
                noRelatedSym.set(sideToFIXSide(order.getSide()));
            }

            noRelatedSym.set(new Symbol(order.getSymbol()));
            noRelatedSym.set(new OrderQty(order.getQuantity()));
            quoteRequest.addGroup(noRelatedSym);

            send(populateOrder(order, quoteRequest), order.getSessionID());
        } else if (order.getType() == OrderType.LIMIT) {
            quickfix.fix44.NewOrderSingle newOrderSingle = new quickfix.fix44.NewOrderSingle(
                new ClOrdID(order.getID()), sideToFIXSide(order.getSide()),
                new TransactTime(), typeToFIXType(order.getType()));

            newOrderSingle.set(new Symbol(order.getSymbol()));
            newOrderSingle.set(new OrderQty(order.getQuantity()));
            newOrderSingle.set(new HandlInst('1'));

            send(populateOrder(order, newOrderSingle), order.getSessionID());

        } else if (order.getType() == OrderType.QUOTE) {
            quickfix.fix44.Quote quote = new quickfix.fix44.Quote(new QuoteID(order.getID()));
            quote.set(new QuoteType(1));
            quote.set(new Symbol(order.getSymbol()));

            if (order.getSide() == OrderSide.BUY) {
                quote.set(new BidPx(order.getLimit()));
                quote.set(new BidSize(order.getQuantity()));  
            } else if (order.getSide() == OrderSide.SELL) {
                quote.set(new OfferPx(order.getLimit()));
                quote.set(new OfferSize(order.getQuantity()));  
            }

            quote.set(new QuoteReqID(order.getQuoteReqID()));
            send(populateOrder(order, quote), order.getSessionID());

        }  else if (order.getType() == OrderType.QUOTE_RESPONSE) {
            quickfix.fix44.QuoteResponse quoteResponse = new quickfix.fix44.QuoteResponse(
                new QuoteRespID(order.getID()),
                new QuoteRespType(order.getQuoteRespType()));

            quoteResponse.set(new QuoteID(order.getQuoteID()));
            quoteResponse.set(new ClOrdID(order.getID()));
            quoteResponse.set(new Price(order.getLimit()));

            //Orderside is earlier copied off the quote, so we need to invert it here for order terms to match
            //(e.g. send a sell response to a buy quote, and buy to a sell quote)
            if (order.getSide() == OrderSide.BUY) {
                quoteResponse.set(sideToFIXSide(OrderSide.SELL));
            } else if (order.getSide() == OrderSide.SELL) {
                quoteResponse.set(sideToFIXSide(OrderSide.BUY));
            } else {
                System.out.println("Unrecognized side");
            }

            OrderQtyData orderQtyData = new OrderQtyData();
            orderQtyData.set(new OrderQty(order.getQuantity()));
            quoteResponse.set(orderQtyData);

            Instrument instrument = new Instrument();
            instrument.set(new Symbol(order.getSymbol()));
            quoteResponse.set(instrument);
            order.setOpen(0);

            send(populateOrder(order, quoteResponse), order.getSessionID());

        } else {
           System.out.println("Something went wrong."); 
        }
    }

    public void send50(Order order) {
        quickfix.fix50.NewOrderSingle newOrderSingle = new quickfix.fix50.NewOrderSingle(
                new ClOrdID(order.getID()), sideToFIXSide(order.getSide()),
                new TransactTime(), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity()));
        newOrderSingle.set(new Symbol(order.getSymbol()));
        newOrderSingle.set(new HandlInst('1'));
        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public quickfix.Message populateOrder(Order order, quickfix.Message message) {
        if (order.getType() == OrderType.LIMIT) {
            message.setField(new Price(order.getLimit()));
            message.setField(tifToFIXTif(order.getTIF()));
        }
        return message;
    }

    public void cancel(Order order) {
        String beginString = order.getSessionID().getBeginString();
        switch (beginString) {
            case "FIX.4.0":
                cancel40(order);
                break;
            case "FIX.4.1":
                cancel41(order);
                break;
            case "FIX.4.2":
                cancel42(order);
                break;
            case "FIX.4.4":
                cancel44(order);
                break;
        }
    }

    public void cancel40(Order order) {
        String id = order.generateID();
        quickfix.fix40.OrderCancelRequest message = new quickfix.fix40.OrderCancelRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(id), new CxlType(CxlType.FULL_REMAINING_QUANTITY), new Symbol(order
                        .getSymbol()), sideToFIXSide(order.getSide()), new OrderQty(order
                        .getQuantity()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());
    }

    public void cancel41(Order order) {
        String id = order.generateID();
        quickfix.fix41.OrderCancelRequest message = new quickfix.fix41.OrderCancelRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(id), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()));
        message.setField(new OrderQty(order.getQuantity()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());
    }

    public void cancel42(Order order) {
        String id = order.generateID();
        quickfix.fix42.OrderCancelRequest message = new quickfix.fix42.OrderCancelRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(id), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), new TransactTime());
        message.setField(new OrderQty(order.getQuantity()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());
    }

    public void cancel44(Order order) {
        String id = order.generateID();
        quickfix.fix44.OrderCancelRequest message = new quickfix.fix44.OrderCancelRequest(
                new OrigClOrdID(order.getID()),
                new ClOrdID(id),
                sideToFIXSide(order.getSide()),
                new TransactTime()
        );

        message.setField(new Symbol(order.getSymbol()));
        message.setField(new OrderQty(order.getQuantity()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());
    }

    public void replace(Order order, Order newOrder) {
        String beginString = order.getSessionID().getBeginString();
        switch (beginString) {
            case "FIX.4.0":
                replace40(order, newOrder);
                break;
            case "FIX.4.1":
                replace41(order, newOrder);
                break;
            case "FIX.4.2":
                replace42(order, newOrder);
                break;
            case "FIX.4.4":
                replace44(order, newOrder);
                break;
        }
    }

    public void replace40(Order order, Order newOrder) {
        quickfix.fix40.OrderCancelReplaceRequest message = new quickfix.fix40.OrderCancelReplaceRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(newOrder.getID()), new HandlInst('1'),
                new Symbol(order.getSymbol()), sideToFIXSide(order.getSide()), new OrderQty(
                        newOrder.getQuantity()), typeToFIXType(order.getType()));

        orderTableModel.addID(order, newOrder.getID());
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }

    public void replace41(Order order, Order newOrder) {
        quickfix.fix41.OrderCancelReplaceRequest message = new quickfix.fix41.OrderCancelReplaceRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(newOrder.getID()), new HandlInst('1'),
                new Symbol(order.getSymbol()), sideToFIXSide(order.getSide()), typeToFIXType(order
                        .getType()));

        orderTableModel.addID(order, newOrder.getID());
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }

    public void replace42(Order order, Order newOrder) {
        quickfix.fix42.OrderCancelReplaceRequest message = new quickfix.fix42.OrderCancelReplaceRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(newOrder.getID()), new HandlInst('1'),
                new Symbol(order.getSymbol()), sideToFIXSide(order.getSide()), new TransactTime(),
                typeToFIXType(order.getType()));

        orderTableModel.addID(order, newOrder.getID());
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }

    public void replace44(Order order, Order newOrder) {
        String newOrderId = newOrder.getID();
        quickfix.fix44.OrderCancelReplaceRequest message = new quickfix.fix44.OrderCancelReplaceRequest(
            new OrigClOrdID(order.getID()),
            new ClOrdID(newOrderId),
            sideToFIXSide(order.getSide()),
            new TransactTime(),
            typeToFIXType(order.getType())
        );

        message.setField(new HandlInst('1'));
        message.setField(new Symbol(order.getSymbol()));
        message.setField(new OrderQty(order.getQuantity()));
        message.setField(new Price(order.getLimit()));

        orderTableModel.addID(order, newOrderId);
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }

    //Helper method for replace
    Message populateCancelReplace(Order order, Order newOrder, quickfix.Message message) {

        if (order.getQuantity() != newOrder.getQuantity())
            message.setField(new OrderQty(newOrder.getQuantity()));
        if (!order.getLimit().equals(newOrder.getLimit()))
            message.setField(new Price(newOrder.getLimit()));
        return message;
    }

    public Side sideToFIXSide(OrderSide side) {
        return (Side) sideMap.getFirst(side);
    }

    public OrderSide FIXSideToSide(Side side) {
        return (OrderSide) sideMap.getSecond(side);
    }

    public OrdType typeToFIXType(OrderType type) {
        return (OrdType) typeMap.getFirst(type);
    }

    public OrderType FIXTypeToType(OrdType type) {
        return (OrderType) typeMap.getSecond(type);
    }

    public TimeInForce tifToFIXTif(OrderTIF tif) {
        return (TimeInForce) tifMap.getFirst(tif);
    }

    public OrderTIF FIXTifToTif(TimeInForce tif) {
        return (OrderTIF) typeMap.getSecond(tif);
    }

    public void addLogonObserver(Observer observer) {
        observableLogon.addObserver(observer);
    }

    public void deleteLogonObserver(Observer observer) {
        observableLogon.deleteObserver(observer);
    }

    public void addOrderObserver(Observer observer) {
        observableOrder.addObserver(observer);
    }

    public void deleteOrderObserver(Observer observer) {
        observableOrder.deleteObserver(observer);
    }

    private static class ObservableOrder extends Observable {
        public void update(Order order) {
            setChanged();
            notifyObservers(order);
            clearChanged();
        }
    }

    private static class ObservableLogon extends Observable {
        public void logon(SessionID sessionID) {
            setChanged();
            notifyObservers(new LogonEvent(sessionID, true));
            clearChanged();
        }

        public void logoff(SessionID sessionID) {
            setChanged();
            notifyObservers(new LogonEvent(sessionID, false));
            clearChanged();
        }
    }

    static {
        //Mapping between custom order sides, types, TIF and FIX order sides, types, TIF
        sideMap.put(OrderSide.BUY, new Side(Side.BUY));
        sideMap.put(OrderSide.SELL, new Side(Side.SELL));

        typeMap.put(OrderType.LIMIT, new OrdType(OrdType.LIMIT));


        tifMap.put(OrderTIF.DAY, new TimeInForce(TimeInForce.DAY));
        tifMap.put(OrderTIF.IOC, new TimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL));
        tifMap.put(OrderTIF.OPG, new TimeInForce(TimeInForce.AT_THE_OPENING));
        tifMap.put(OrderTIF.GTC, new TimeInForce(TimeInForce.GOOD_TILL_CANCEL));
        tifMap.put(OrderTIF.GTX, new TimeInForce(TimeInForce.GOOD_TILL_CROSSING));
    }

    public boolean isMissingField() {
        return isMissingField;
    }

    public void setMissingField(boolean isMissingField) {
        this.isMissingField = isMissingField;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }
}
