/*
 * Copyright (C) 2016 Clover Network, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clover.remote.client.lib.example;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import com.clover.remote.client.CloverConnector;
import com.clover.remote.client.ICloverConnector;
import com.clover.remote.client.lib.example.adapter.ItemsListViewAdapter;
import com.clover.remote.client.lib.example.adapter.OrdersListViewAdapter;
import com.clover.remote.client.lib.example.adapter.PaymentsListViewAdapter;
import com.clover.remote.client.lib.example.model.OrderObserver;
import com.clover.remote.client.lib.example.model.POSCard;
import com.clover.remote.client.lib.example.model.POSDiscount;
import com.clover.remote.client.lib.example.model.POSExchange;
import com.clover.remote.client.lib.example.model.POSLineItem;
import com.clover.remote.client.lib.example.model.POSNakedRefund;
import com.clover.remote.client.lib.example.model.POSOrder;
import com.clover.remote.client.lib.example.model.POSPayment;
import com.clover.remote.client.lib.example.model.POSRefund;
import com.clover.remote.client.lib.example.model.POSStore;
import com.clover.remote.client.lib.example.model.StoreObserver;
import com.clover.remote.client.messages.RefundPaymentRequest;
import com.clover.remote.client.messages.TipAdjustAuthRequest;
import com.clover.remote.client.messages.VoidPaymentRequest;
import com.clover.sdk.v3.order.VoidReason;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OrdersFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link OrdersFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class OrdersFragment extends Fragment implements OrderObserver {
  private static final String ARG_STORE = "store";

  private POSStore store;

  private OnFragmentInteractionListener mListener;

  private WeakReference<ICloverConnector> cloverConnectorWeakReference;
  private ListView itemsListView;
  private View view;
  private ListView ordersListView;
  POSOrder selectedOrder = null;

  /**
   * Use this factory method to create a new instance of
   * this fragment using the provided parameters.
   *
   * @param store           Parameter 1.
   * @param cloverConnector Parameter 2.
   * @return A new instance of fragment OrdersFragment.
   */
  // TODO: Rename and change types and number of parameters
  public static OrdersFragment newInstance(POSStore store, CloverConnector cloverConnector) {
    OrdersFragment fragment = new OrdersFragment();
    fragment.setStore(store);
    Bundle args = new Bundle();
    fragment.setArguments(args);

    return fragment;
  }

  public OrdersFragment() {
    // Required empty public constructor
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override public void onDestroy() {
    super.onDestroy();
    if(selectedOrder != null) {
      selectedOrder.removeObserver(this);
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    view = inflater.inflate(R.layout.fragment_orders, container, false);

    itemsListView = (ListView) view.findViewById(R.id.ItemsGridView);
    final ItemsListViewAdapter itemsListViewAdapter = new ItemsListViewAdapter(view.getContext(), R.id.ItemsGridView, Collections.EMPTY_LIST);
    itemsListView.setAdapter(itemsListViewAdapter);

    final ListView paymentsListView = (ListView) view.findViewById(R.id.PaymentsGridView);
    final PaymentsListViewAdapter paymentsListViewAdapter = new PaymentsListViewAdapter(view.getContext(), R.id.PaymentsGridView, Collections.EMPTY_LIST);
    itemsListView.setAdapter(paymentsListViewAdapter);

    ordersListView = (ListView) view.findViewById(R.id.OrdersListView);
    OrdersListViewAdapter ordersListViewAdapter = new OrdersListViewAdapter(view.getContext(), R.id.OrdersListView, store.getOrders());
    ordersListView.setAdapter(ordersListViewAdapter);
    ordersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if(selectedOrder != null) {
          selectedOrder.removeObserver(OrdersFragment.this);
        }
        POSOrder posOrder = (POSOrder) ordersListView.getItemAtPosition(position);
        selectedOrder = posOrder;
        posOrder.addOrderObserver(OrdersFragment.this);
        updateDisplaysForOrder(posOrder);
      }
    });

    paymentsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        final POSExchange posExchange = (POSExchange) paymentsListView.getItemAtPosition(position);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        String[] options = null;

        if (posExchange instanceof POSPayment) {
          if (((POSPayment)posExchange).getPaymentStatus() == POSPayment.Status.AUTHORIZED) {
            options = new String[]{"Void Payment", "Refund Payment", "Tip Adjust Payment", "Receipt Options"};
          } else if (((POSPayment)posExchange).getPaymentStatus() == POSPayment.Status.PAID) {
            options = new String[]{"Void Payment", "Refund Payment", "Receipt Options"};
          } else {
            return;
          }
          final String[] finalPaymentOptions = options;
          builder.setTitle("Payment Actions").
              setItems(finalPaymentOptions, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int index) {
                  final ICloverConnector cloverConnector = cloverConnectorWeakReference.get();
                  if (cloverConnector != null) {
                    final String option = finalPaymentOptions[index];
                    switch (option) {
                      case "Void Payment": {
                        VoidPaymentRequest vpr = new VoidPaymentRequest();
                        vpr.setPaymentId(posExchange.getPaymentID());
                        vpr.setOrderId(posExchange.getOrderId());
                        vpr.setVoidReason(VoidReason.USER_CANCEL.name());
                        cloverConnector.voidPayment(vpr);
                        //dlg.disiss();
                        break;
                      }
                      case "Refund Payment": {
                        RefundPaymentRequest rpr = new RefundPaymentRequest();
                        rpr.setPaymentId(posExchange.getPaymentID());
                        rpr.setOrderId(posExchange.orderID);
                        cloverConnector.refundPayment(rpr);
                        break;
                      }
                      case "Tip Adjust Payment": {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        final EditText input = new EditText(getActivity());
                        input.setInputType(InputType.TYPE_CLASS_NUMBER);
                        builder.setView(input);

                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                          @Override
                          public void onClick(DialogInterface dialog, int which) {
                            double val = Double.parseDouble(input.getText().toString());
                            long value = (long) val;

                            TipAdjustAuthRequest taar = new TipAdjustAuthRequest();
                            taar.setPaymentID(posExchange.getPaymentID());
                            taar.setOrderID(posExchange.getOrderId());
                            taar.setTipAmount(value);
                            cloverConnector.tipAdjustAuth(taar);
                            dialog.dismiss();
                          }
                        });
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                          @Override
                          public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                          }
                        });

                        builder.show();
                        break;
                      }
                      case "Receipt Options": {
                        cloverConnector.displayPaymentReceiptOptions(posExchange.orderID, posExchange.getPaymentID());
                        break;
                      }

                    }
                  } else {
                    Toast.makeText(getActivity().getBaseContext(), "Clover Connector is null", Toast.LENGTH_LONG).show();
                  }
                }
              });
          final Dialog dlg = builder.create();
          dlg.show();
        } /*else if (posExchange instanceof POSRefund) {  //TODO: Add this when the supporting remote-pay version is released
          options = new String[]{"Receipt Options"};
          final String[] finalRefundOptions = options;
          builder.setTitle("Refund Actions").
              setItems(finalRefundOptions, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int index) {
                  final ICloverConnector cloverConnector = cloverConnectorWeakReference.get();
                  if (cloverConnector != null) {
                    final String option = finalRefundOptions[index];
                    switch (option) {
                      case "Receipt Options": {
                        cloverConnector.displayRefundReceiptOptions(posExchange.orderID, ((POSRefund)posExchange).getRefundID());
                        break;
                      }
                    }
                  } else {
                    Toast.makeText(getActivity().getBaseContext(), "Clover Connector is null", Toast.LENGTH_LONG).show();
                  }
                }
              });
          final Dialog dlg = builder.create();
          dlg.show();
        } */

      }
    });

    return view;
  }

  private void updateDisplaysForOrder(POSOrder posOrder) {
    updateItems(posOrder);
    updatePayments(posOrder);
  }

  private void updateItems(final POSOrder posOrder) {
    getView().post(new Runnable(){
      @Override public void run() {
        final ListView itemsListView = (ListView) view.findViewById(R.id.ItemsGridView);
        ItemsListViewAdapter itemsListViewAdapter = new ItemsListViewAdapter(view.getContext(), R.id.ItemsGridView, posOrder.getItems());
        itemsListView.setAdapter(itemsListViewAdapter);
      }
    });
  }

  private void updatePayments(final POSOrder posOrder) {
    getView().post(new Runnable() {
      @Override public void run() {
        final ListView paymentsListView = (ListView) view.findViewById(R.id.PaymentsGridView);
        PaymentsListViewAdapter paymentsListViewAdapter = new PaymentsListViewAdapter(view.getContext(), R.id.PaymentsGridView, posOrder.getPayments());
        paymentsListView.setAdapter(paymentsListViewAdapter);
      }
    });
  }

  // TODO: Rename method, update argument and hook method into UI event
  public void onButtonPressed(Uri uri) {
    if (mListener != null) {
      mListener.onFragmentInteraction(uri);
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      mListener = (OnFragmentInteractionListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString()
          + " must implement OnFragmentInteractionListener");
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mListener = null;
  }

  public void setStore(final POSStore store) {
    this.store = store;

    store.addStoreObserver(new StoreObserver() {
      @Override
      public void newOrderCreated(POSOrder order) {
        List<POSOrder> orders = new ArrayList<POSOrder>(store.getOrders().size());
        List<POSOrder> storeOrders = store.getOrders();
        for(int i=storeOrders.size()-1; i>=0; i--) {
          orders.add(0, storeOrders.get(i)); // newest first...
        }
        OrdersListViewAdapter listViewAdapter = new OrdersListViewAdapter(view.getContext(), R.id.ItemsGridView, orders);
        ordersListView.setAdapter(listViewAdapter);
      }

      @Override public void cardAdded(POSCard card) {

      }

      @Override public void refundAdded(POSNakedRefund refund) {

      }

      @Override public void preAuthAdded(POSPayment payment) {

      }

      @Override public void preAuthRemoved(POSPayment payment) {

      }
    });

  }

  @Override public void lineItemAdded(POSOrder posOrder, POSLineItem lineItem) {

  }

  @Override public void lineItemRemoved(POSOrder posOrder, POSLineItem lineItem) {

  }

  @Override public void lineItemChanged(POSOrder posOrder, POSLineItem lineItem) {

  }

  @Override public void paymentAdded(POSOrder posOrder, POSPayment payment) {

  }

  @Override public void refundAdded(POSOrder posOrder, POSRefund refund) {
    updateDisplaysForOrder(posOrder);
  }

  @Override public void paymentChanged(POSOrder posOrder, POSExchange pay) {
    updatePayments(posOrder);
  }

  @Override public void discountAdded(POSOrder posOrder, POSDiscount discount) {

  }

  @Override public void discountChanged(POSOrder posOrder, POSDiscount discount) {

  }

  /**
   * This interface must be implemented by activities that contain this
   * fragment to allow an interaction in this fragment to be communicated
   * to the activity and potentially other fragments contained in that
   * activity.
   * <p>
   * See the Android Training lesson <a href=
   * "http://developer.android.com/training/basics/fragments/communicating.html"
   * >Communicating with Other Fragments</a> for more information.
   */
  public interface OnFragmentInteractionListener {
    // TODO: Update argument type and name
    public void onFragmentInteraction(Uri uri);
  }

  public void setCloverConnector(ICloverConnector cloverConnector) {
    cloverConnectorWeakReference = new WeakReference<ICloverConnector>(cloverConnector);
  }

}
