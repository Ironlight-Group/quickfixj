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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import quickfix.examples.banzai.*;

public class CancelReplacePanel extends JPanel {
    private final JLabel quantityLabel = new JLabel("Quantity");
    private final JLabel limitPriceLabel = new JLabel("Limit");
    private final JLabel quotePriceLabel = new JLabel("Price");
    private final IntegerNumberTextField quantityTextField = new IntegerNumberTextField();
    private final DoubleNumberTextField limitPriceTextField = new DoubleNumberTextField();
    private final DoubleNumberTextField quotePriceTextField = new DoubleNumberTextField();
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton replaceButton = new JButton("Replace");
    private final JButton hitLiftButton = new JButton("Hit/Lift");
    private Order order = null;

    private final GridBagConstraints constraints = new GridBagConstraints();

    private final BanzaiApplication application;

    public CancelReplacePanel(final BanzaiApplication application) {
        this.application = application;
        cancelButton.addActionListener(new CancelListener());
        replaceButton.addActionListener(new ReplaceListener());
        hitLiftButton.addActionListener(new HitLiftListener());

        setLayout(new GridBagLayout());
        createComponents();
    }

    public void addActionListener(ActionListener listener) {
        cancelButton.addActionListener(listener);
        replaceButton.addActionListener(listener);
        hitLiftButton.addActionListener(listener);
    }

    private void createComponents() {
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;

        int x = 0;
        int y = 0;

        constraints.insets = new Insets(0, 0, 5, 5);
        add(cancelButton, x, y);
        add(replaceButton, ++x, y);
        constraints.weightx = 0;
        add(quantityLabel, ++x, y);
        constraints.weightx = 5;
        add(quantityTextField, ++x, y);
        constraints.weightx = 0;
        add(limitPriceLabel, ++x, y);
        constraints.weightx = 5;
        add(limitPriceTextField, ++x, y);
        constraints.weightx = 1;
        add(hitLiftButton, ++x, y);
    }

    public void setEnabled(boolean enabled) {
        cancelButton.setEnabled(enabled);
        replaceButton.setEnabled(enabled);
        quantityTextField.setEnabled(enabled);
        limitPriceTextField.setEnabled(enabled);
        quotePriceTextField.setEnabled(enabled);
        hitLiftButton.setEnabled(enabled);

        Color labelColor = enabled ? Color.black : Color.gray;
        Color bgColor = enabled ? Color.white : Color.gray;
        quantityTextField.setBackground(bgColor);
        limitPriceTextField.setBackground(bgColor);
        quotePriceTextField.setBackground(bgColor);
        quantityLabel.setForeground(labelColor);
        limitPriceLabel.setForeground(labelColor);
        quotePriceLabel.setForeground(labelColor);
    }

    public void update() {
        setOrder(this.order);
    }

    //TODO hit/lift based on this order data
    public void setOrder(Order order) {
        if (order == null)
            return;
        this.order = order;
        quantityTextField.setText
        (Integer.toString(order.getOpen()));

        Double limit = order.getLimit();
        if (limit != null)
            limitPriceTextField.setText(order.getLimit().toString());
        setEnabled(order.getOpen() > 0);
    }

    private JComponent add(JComponent component, int x, int y) {
        constraints.gridx = x;
        constraints.gridy = y;
        add(component, constraints);
        return component;
    }

    private class CancelListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            application.cancel(order);
        }
    }

    private class ReplaceListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            Order newOrder = (Order) order.clone();
            newOrder.setQuantity
            (Integer.parseInt(quantityTextField.getText()));
            newOrder.setLimit(Double.parseDouble(limitPriceTextField.getText()));
            newOrder.setRejected(false);
            newOrder.setCanceled(false);
            newOrder.setOpen(0);
            newOrder.setExecuted(0);

            application.replace(order, newOrder);
        }
    }

    private class HitLiftListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (order.getType() != OrderType.QUOTE) {
                return;
            }
            Order quoteResponse = new Order();
            quoteResponse.setType(OrderType.QUOTE_RESPONSE);
            quoteResponse.setQuoteRespType(1);

            quoteResponse.setQuoteID(order.getQuoteID());

            if (order.getSide() == OrderSide.BUY) {

            } else if (order.getSide() == OrderSide.SELL) {
                System.out.println("Lifted.");
            } else {
                System.out.println("Unsupported Side.");
                return;
            }
            application.send(quoteResponse);
        }
    }
}
