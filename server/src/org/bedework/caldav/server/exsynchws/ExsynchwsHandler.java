/* **********************************************************************
    Copyright 2009 Rensselaer Polytechnic Institute. All worldwide rights reserved.

    Redistribution and use of this distribution in source and binary forms,
    with or without modification, are permitted provided that:
       The above copyright notice and this permission notice appear in all
        copies and supporting documentation;

        The name, identifiers, and trademarks of Rensselaer Polytechnic
        Institute are not used in advertising or publicity without the
        express prior written permission of Rensselaer Polytechnic Institute;

    DISCLAIMER: The software is distributed" AS IS" without any express or
    implied warranty, including but not limited to, any implied warranties
    of merchantability or fitness for a particular purpose or any warrant)'
    of non-infringement of any current or pending patent rights. The authors
    of the software make no representations about the suitability of this
    software for any particular purpose. The entire risk as to the quality
    and performance of the software is with the user. Should the software
    prove defective, the user assumes the cost of all necessary servicing,
    repair or correction. In particular, neither Rensselaer Polytechnic
    Institute, nor the authors of the software are liable for any indirect,
    special, consequential, or incidental damages related to the software,
    to the maximum extent the law permits.
*/
package org.bedework.caldav.server.exsynchws;

import org.bedework.caldav.server.CaldavBWIntf;
import org.bedework.caldav.server.CaldavComponentNode;
import org.bedework.caldav.server.PostMethod.RequestPars;
import org.bedework.caldav.server.sysinterface.SysIntf;
import org.bedework.exsynch.wsmessages.AddItem;
import org.bedework.exsynch.wsmessages.AddItemResponse;
import org.bedework.exsynch.wsmessages.FetchItem;
import org.bedework.exsynch.wsmessages.GetSycnchInfo;
import org.bedework.exsynch.wsmessages.ObjectFactory;
import org.bedework.exsynch.wsmessages.StartServiceNotification;
import org.bedework.exsynch.wsmessages.StartServiceResponse;
import org.bedework.exsynch.wsmessages.StatusType;
import org.bedework.exsynch.wsmessages.SynchInfoResponse;
import org.bedework.exsynch.wsmessages.SynchInfoType;
import org.bedework.exsynch.wsmessages.SynchInfoResponse.SynchInfoResponses;

import edu.rpi.cct.webdav.servlet.common.MethodBase;
import edu.rpi.cct.webdav.servlet.shared.WebdavException;
import edu.rpi.cct.webdav.servlet.shared.WebdavNsIntf;
import edu.rpi.cct.webdav.servlet.shared.WebdavNsNode;

import org.w3c.dom.Document;

import java.io.OutputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPMessage;

/** Class extended by classes which handle special GET requests, e.g. the
 * freebusy service, web calendars, ischedule etc.
 *
 * @author Mike Douglass
 */
public class ExsynchwsHandler extends MethodBase {
  protected CaldavBWIntf intf;

  private MessageFactory soapMsgFactory;
  private JAXBContext jc;

  /** This represents an active connection to a synch engine. It's possible we
   * would have more than one of these running I guess. For the moment we'll
   * only have one but these probably need a table indexed by url.
   *
   */
  class ActiveConnectionInfo {
    String subscribeUrl;

    String synchToken;
  }

  static volatile Object monitor = new Object();

  static ActiveConnectionInfo activeConnection;

  ObjectFactory of = new ObjectFactory();

  /**
   * @param intf
   * @throws WebdavException
   */
  public ExsynchwsHandler(final CaldavBWIntf intf) throws WebdavException {
    this.intf = intf;

    try {
      if (soapMsgFactory == null) {
        soapMsgFactory = MessageFactory.newInstance();
      }

      if (jc == null) {
        jc = JAXBContext.newInstance("org.bedework.exsynch.wsmessages");
      }
    } catch(Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void init() {
  }

  @Override
  public void doMethod(final HttpServletRequest req,
                       final HttpServletResponse resp)
        throws WebdavException {

  }


  /**
   * @param req
   * @param resp
   * @param pars
   * @throws WebdavException
   */
  public void processPost(final HttpServletRequest req,
                          final HttpServletResponse resp,
                          final RequestPars pars) throws WebdavException {

    try {
      SOAPMessage msg = soapMsgFactory.createMessage(null, // headers
                                                     req.getInputStream());


      SOAPBody body = msg.getSOAPBody();

      Unmarshaller u = jc.createUnmarshaller();

      Object o = u.unmarshal(body.getFirstChild());
      if (o instanceof JAXBElement) {
        o = ((JAXBElement)o).getValue();
      }

      if (o instanceof GetSycnchInfo) {
        doGetSycnchInfo((GetSycnchInfo)o, req, resp);
        return;
      }

      if (o instanceof StartServiceNotification) {
        doStartService((StartServiceNotification)o, resp);
        return;
      }

      if (o instanceof AddItem) {
        doAddItem((AddItem)o, req, resp);
        return;
      }

      if (o instanceof FetchItem) {
        doFetchItem((FetchItem)o, req, resp);
        return;
      }

      throw new WebdavException("Unhandled request");
    } catch (WebdavException we) {
      throw we;
    } catch(Throwable t) {
      throw new WebdavException(t);
    }
  }

  /**
   * @return current account
   */
  public String getAccount() {
    return intf.getAccount();
  }

  /**
   * @return SysIntf
   */
  public SysIntf getSysi() {
    return intf.getSysi();
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void doStartService(final StartServiceNotification ssn,
                              final HttpServletResponse resp) throws WebdavException {
    if (debug) {
      trace("StartServiceNotification: url=" + ssn.getSubscribeUrl() +
            "\n                token=" + ssn.getToken());
    }

    synchronized (monitor) {
      if (activeConnection == null) {
        activeConnection = new ActiveConnectionInfo();
      }

      activeConnection.subscribeUrl = ssn.getSubscribeUrl();
      activeConnection.synchToken = ssn.getToken();
    }

    notificationResponse(resp, true);
  }

  private void notificationResponse(final HttpServletResponse resp,
                                    final boolean ok) throws WebdavException {
    try {
      resp.setCharacterEncoding("UTF-8");
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("text/xml; charset=UTF-8");

      StartServiceResponse ssr = of.createStartServiceResponse();

      if (ok) {
        ssr.setStatus(StatusType.OK);
      } else {
        ssr.setStatus(StatusType.ERROR);
      }

      ssr.setToken(activeConnection.synchToken);

      marshal(ssr, resp.getOutputStream());
    } catch (WebdavException we) {
      throw we;
    } catch(Throwable t) {
      throw new WebdavException(t);
    }
  }

  private void marshal(final Object o, final OutputStream out) throws WebdavException {
    try {
      Marshaller marshaller = jc.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      Document doc = dbf.newDocumentBuilder().newDocument();

      SOAPMessage msg = soapMsgFactory.createMessage();
      msg.getSOAPBody().addDocument(doc);

      marshaller.marshal(o,
                         msg.getSOAPBody());

      msg.writeTo(out);
    } catch(Throwable t) {
      throw new WebdavException(t);
    }
  }

  private void doGetSycnchInfo(final GetSycnchInfo gsi,
                               final HttpServletRequest req,
                               final HttpServletResponse resp) throws WebdavException {
    if (debug) {
      trace("GetSycnchInfo: cal=" + gsi.getCalendarHref() +
            "\n       principal=" + gsi.getPrincipalHref() +
            "\n           token=" + gsi.getSynchToken());
    }

    intf.reAuth(req, gsi.getPrincipalHref());

    if (!gsi.getSynchToken().equals(activeConnection.synchToken)) {
      throw new WebdavException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                "Invalid synch token");
    }

    SynchInfoResponse sir = new SynchInfoResponse();

    sir.setCalendarHref(gsi.getCalendarHref());

    WebdavNsNode calNode = intf.getNode(gsi.getCalendarHref(),
                                        WebdavNsIntf.existanceMust,
                                        WebdavNsIntf.nodeTypeCollection);

    if (calNode == null) {
      // Drop this subscription
      throw new WebdavException("Unreachable " + gsi.getCalendarHref());
    }

    List<SynchInfoType> sis = new Report(nsIntf).query(gsi.getCalendarHref());

    SynchInfoResponses sirs = new SynchInfoResponses();
    sir.setSynchInfoResponses(sirs);
    sirs.getSynchInfos().addAll(sis);

    try {
      marshal(sir, resp.getOutputStream());
    } catch (WebdavException we) {
      throw we;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private void doAddItem(final AddItem ai,
                         final HttpServletRequest req,
                         final HttpServletResponse resp) throws WebdavException {
    if (debug) {
      trace("AddItem:       cal=" + ai.getCalendarHref() +
            "\n       principal=" + ai.getPrincipalHref() +
            "\n           token=" + ai.getSynchToken());
    }

    intf.reAuth(req, ai.getPrincipalHref());

    if (!ai.getSynchToken().equals(activeConnection.synchToken)) {
      throw new WebdavException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                "Invalid synch token");
    }

    String name = ai.getCalendarHref() + "/" + ai.getUid() + ".ics";

    WebdavNsNode elNode = intf.getNode(name,
                                        WebdavNsIntf.existanceNot,
                                        WebdavNsIntf.nodeTypeEntity);

    boolean added = false;
    String msg = null;

    try {
      added = intf.putEvent(req, (CaldavComponentNode)elNode,
                                  ai.getIcalendar(),
                                  true, null);
    } catch (Throwable t) {
      if (debug) {
        error(t);
        msg = t.getLocalizedMessage();
      }
    }

    AddItemResponse air = new AddItemResponse();

    if (added) {
      air.setStatus(StatusType.OK);
    } else {
      air.setStatus(StatusType.ERROR);
    }

    try {
      marshal(air, resp.getOutputStream());
    } catch (WebdavException we) {
      throw we;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private void doFetchItem(final FetchItem fi,
                           final HttpServletRequest req,
                           final HttpServletResponse resp) throws WebdavException {
    if (debug) {
      trace("AddItem:       cal=" + fi.getCalendarHref() +
            "\n       principal=" + fi.getPrincipalHref() +
            "\n           token=" + fi.getSynchToken());
    }

    intf.reAuth(req, fi.getPrincipalHref());

    if (!fi.getSynchToken().equals(activeConnection.synchToken)) {
      throw new WebdavException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                "Invalid synch token");
    }
  }
}
