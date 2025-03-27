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

package quickfix.examples.banzai.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import quickfix.SessionID;
import quickfix.examples.banzai.BanzaiApplication;
import quickfix.examples.banzai.DoubleNumberTextField;
import quickfix.examples.banzai.IntegerNumberTextField;
import quickfix.examples.banzai.LogonEvent;
import quickfix.examples.banzai.Order;
import quickfix.examples.banzai.OrderSide;
import quickfix.examples.banzai.OrderTIF;
import quickfix.examples.banzai.OrderTableModel;
import quickfix.examples.banzai.OrderType;

@SuppressWarnings("unchecked")
public class OrderEntryPanel extends JPanel implements Observer {
    private boolean symbolEntered = false;
    private boolean quantityEntered = false;
    private boolean limitEntered = false;
    private boolean sessionEntered = false;

    private final JTextField symbolTextField = new JTextField();
    private final IntegerNumberTextField quantityTextField = new IntegerNumberTextField();

    private final JComboBox sideComboBox = new JComboBox(OrderSide.toArray());
    private final JComboBox typeComboBox = new JComboBox(OrderType.toArray());
    private final JComboBox tifComboBox = new JComboBox(OrderTIF.toArray());

    private final DoubleNumberTextField limitPriceTextField = new DoubleNumberTextField();

    private final JComboBox sessionComboBox = new JComboBox();

    private final JLabel limitPriceLabel = new JLabel("Limit");

    private final JLabel messageLabel = new JLabel(" ");
    private final JButton submitButton = new JButton("Submit");

    private OrderTableModel orderTableModel = null;
    private transient BanzaiApplication application = null;

    private final GridBagConstraints constraints = new GridBagConstraints();

    private Order selectedOrder = null;

    public OrderEntryPanel(final OrderTableModel orderTableModel,
                final BanzaiApplication application) {
        setName("OrderEntryPanel");
        this.orderTableModel = orderTableModel;
        this.application = application;

        application.addLogonObserver(this);

        SubmitActivator activator = new SubmitActivator();
        symbolTextField.addKeyListener(activator);
        quantityTextField.addKeyListener(activator);
        limitPriceTextField.addKeyListener(activator);
        sessionComboBox.addItemListener(activator);

        setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        setLayout(new GridBagLayout());
        createComponents();
    }

    public void addActionListener(ActionListener listener) {
        submitButton.addActionListener(listener);
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
        if (message == null || message.equals(""))
            messageLabel.setText(" ");
    }

    public void clearMessage() {
        setMessage(null);
    }

    private void createComponents() {
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;

        int x = 0;
        int y = 0;

        add(new JLabel("Symbol"), x, y);
        add(new JLabel("Quantity"), ++x, y);
        add(new JLabel("Side"), ++x, y);
        add(new JLabel("Type"), ++x, y);
        constraints.ipadx = 30;
        add(limitPriceLabel, ++x, y);
        constraints.ipadx = 0;
        add(new JLabel("TIF"), ++x, y);
        constraints.ipadx = 30;

        symbolTextField.setName("SymbolTextField");
        add(symbolTextField, x = 0, ++y);
        constraints.ipadx = 0;
        quantityTextField.setName("QuantityTextField");
        add(quantityTextField, ++x, y);
        sideComboBox.setName("SideComboBox");
        add(sideComboBox, ++x, y);
        typeComboBox.setName("TypeComboBox");
        add(typeComboBox, ++x, y);
        limitPriceTextField.setName("LimitPriceTextField");
        add(limitPriceTextField, ++x, y);
        tifComboBox.setName("TifComboBox");
        add(tifComboBox, ++x, y);

        constraints.insets = new Insets(3, 0, 0, 0);
        constraints.gridwidth = GridBagConstraints.RELATIVE;
        sessionComboBox.setName("SessionComboBox");
        add(sessionComboBox, 0, ++y);
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        submitButton.setName("SubmitButton");
        add(submitButton, x, y);
        constraints.gridwidth = 0;
        add(messageLabel, 0, ++y);

        typeComboBox.addItemListener(new PriceListener());

        Font font = new Font(messageLabel.getFont().getFontName(), Font.BOLD, 12);
        messageLabel.setFont(font);
        messageLabel.setForeground(Color.red);
        messageLabel.setHorizontalAlignment(JLabel.CENTER);
        submitButton.setEnabled(false);
        submitButton.addActionListener(new SubmitListener());
        activateSubmit();
    }

    private JComponent add(JComponent component, int x, int y) {
        constraints.gridx = x;
        constraints.gridy = y;
        add(component, constraints);
        return component;
    }

    //Determines whether the submit button is enabled based on
    //whether enough information has been entered to construct a valid order of the selected type.
    private void activateSubmit() {
        OrderType type = (OrderType) typeComboBox.getSelectedItem();
        boolean activate = symbolEntered && quantityEntered && sessionEntered;

        if (type == OrderType.LIMIT) {
            submitButton.setEnabled(sessionEntered && limitEntered && quantityEntered && symbolEntered);
        } else if (type == OrderType.RFQ) {
            submitButton.setEnabled(sessionEntered && quantityEntered && symbolEntered);
        } else if (type == OrderType.QUOTE) {
            submitButton.setEnabled(sessionEntered && limitEntered);
        } else if (type == OrderType.QUOTE_RESPONSE) {
            submitButton.setEnabled(sessionEntered);
        }
    }

    //Enables and disables price, side, quantity depending on whether it's appropriate for the order type
    private class PriceListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            OrderType item = (OrderType) typeComboBox.getSelectedItem();
            if (item == OrderType.LIMIT) {
                enableLimitPrice(true);
                enableQuantity(true);
                enableOrderSide(true);
                enableSymbol(true);
            } else if (item == OrderType.RFQ) {
                enableLimitPrice(false);
                enableQuantity(true);
                enableOrderSide(true);
                enableSymbol(true);
            } else if (item == OrderType.QUOTE) {
                enableLimitPrice(true);
                enableQuantity(false);
                enableOrderSide(true);
                enableSymbol(false);
            } else if (item == OrderType.QUOTE_RESPONSE) {
                enableLimitPrice(false);
                enableQuantity(false);
                enableOrderSide(false);
                enableSymbol(false);
            }
            activateSubmit();
        }

        private void enableSymbol(boolean enabled) {
            Color bgColor = enabled ? Color.white : Color.gray;
            symbolTextField.setEnabled(enabled);
            symbolTextField.setBackground(bgColor);
        }

        private void enableQuantity(boolean enabled) {
            Color bgColor = enabled ? Color.white : Color.gray;
            quantityTextField.setEnabled(enabled);
            quantityTextField.setBackground(bgColor);
        }

        private void enableOrderSide(boolean enabled) {
            Color bgColor = enabled ? Color.white : Color.gray;
            sideComboBox.setEnabled(enabled);
            sideComboBox.setBackground(bgColor);
        }

        private void enableLimitPrice(boolean enabled) {
            Color bgColor = enabled ? Color.white : Color.gray;
            limitPriceTextField.setEnabled(enabled);
            limitPriceTextField.setBackground(bgColor);
        }
    }

    public void update(Observable o, Object arg) {
        LogonEvent logonEvent = (LogonEvent) arg;
        if (logonEvent.isLoggedOn())
            sessionComboBox.addItem(logonEvent.getSessionID());
        else
            sessionComboBox.removeItem(logonEvent.getSessionID());
    }

    public void setSelectedOrder(Order selectedOrder) {
        this.selectedOrder = selectedOrder;
    }

    private class SubmitListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            Order order = new Order();
            order.setType((OrderType) typeComboBox.getSelectedItem());
            order.setTIF((OrderTIF) tifComboBox.getSelectedItem());

            OrderType type = order.getType();
            if (type == OrderType.LIMIT) {
                order.setSide((OrderSide) sideComboBox.getSelectedItem());
                order.setSymbol(symbolTextField.getText());
                order.setLimit(limitPriceTextField.getText());
                order.setQuantity(Integer.parseInt(quantityTextField.getText()));
            } else if (type == OrderType.RFQ) {
                order.setSide((OrderSide) sideComboBox.getSelectedItem());
                order.setSymbol(symbolTextField.getText());
                order.setQuantity(Integer.parseInt(quantityTextField.getText()));
            } else if (type == OrderType.QUOTE) {
                order.setSide((OrderSide) sideComboBox.getSelectedItem());
                order.setSymbol(selectedOrder.getSymbol());
                order.setQuoteReqID(selectedOrder.getQuoteReqID());
                order.setLimit(limitPriceTextField.getText());
                order.setQuantity(selectedOrder.getQuantity());
            } else if (type == OrderType.QUOTE_RESPONSE) {
                order.setQuoteRespType(1);
                order.setQuoteID(selectedOrder.getQuoteID());
                order.setSymbol(selectedOrder.getSymbol());
                order.setLimit(selectedOrder.getLimit());
                order.setQuantity(selectedOrder.getQuantity());
                order.setSide(selectedOrder.getSide());
            }

            order.setSessionID((SessionID) sessionComboBox.getSelectedItem());
            order.setOpen(order.getQuantity());

            orderTableModel.addOrder(order);
            application.send(order);
        }
    }

    private class SubmitActivator implements KeyListener, ItemListener {
        public void keyReleased(KeyEvent e) {
            Object obj = e.getSource();
            if (obj == symbolTextField) {
                symbolEntered = testField(obj);
            } else if (obj == quantityTextField) {
                quantityEntered = testField(obj);
            } else if (obj == limitPriceTextField) {
                limitEntered = testField(obj);
            }
            activateSubmit();
        }

        public void itemStateChanged(ItemEvent e) {
            sessionEntered = sessionComboBox.getSelectedItem() != null;
            activateSubmit();
        }

        private boolean testField(Object o) {
            String value = ((JTextField) o).getText();
            value = value.trim();
            return value.length() > 0;
        }

        public void keyTyped(KeyEvent e) {}

        public void keyPressed(KeyEvent e) {}
    }
}
