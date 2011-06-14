/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.caldav.server.sysinterface;

import org.bedework.caldav.server.CalDAVCollection;
import org.bedework.caldav.server.CalDAVEvent;
import org.bedework.caldav.server.CalDAVResource;
import org.bedework.caldav.server.PropertyHandler;
import org.bedework.caldav.server.SysiIcalendar;
import org.bedework.caldav.server.PropertyHandler.PropertyType;
import org.bedework.caldav.util.CalDAVConfig;
import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.FilterBase;

import edu.rpi.cct.webdav.servlet.shared.PrincipalPropertySearch;
import edu.rpi.cct.webdav.servlet.shared.UrlHandler;
import edu.rpi.cct.webdav.servlet.shared.WdEntity;
import edu.rpi.cct.webdav.servlet.shared.WebdavException;
import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.cmt.access.Acl;
import edu.rpi.cmt.access.Acl.CurrentAccess;
import edu.rpi.cmt.calendar.ScheduleStates;
import edu.rpi.sss.util.xml.XmlEmit;

import net.fortuna.ical4j.model.Calendar;

import org.oasis_open.docs.ns.wscal.calws_soap.SelectElementType;

import ietf.params.xml.ns.icalendar_2.IcalendarType;

import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/** All interactions with the underlying calendar system are made via this
 * interface.
 *
 * <p>We're using the bedework object classes here. To simplify matters (a little)
 * we don't have distinct event, todo and journal classes. They are all currently
 * the BwEvent class with an entityType defining what the object represents.
 *
 * @author Mike Douglass douglm at rpi.edu
 */
public interface SysIntf {
  /** Called before any other method is called to allow initialization to
   * take place at the first or subsequent requests
   *
   * @param req
   * @param account
   * @param conf  per application type configuration
   * @param debug
   * @throws WebdavException
   */
  public void init(HttpServletRequest req,
                   String account,
                   CalDAVConfig conf,
                   boolean debug) throws WebdavException;

  /** Return CalDAV relevant properties about the system.
   *
   * @return SystemProperties object - never null.
   * @throws WebdavException
   */
  public SystemProperties getSystemProperties() throws WebdavException;

  /** Return default content type for this service.
   *
   * @return String - never null.
   * @throws WebdavException
   */
  public String getDefaultContentType() throws WebdavException;

  /** Return the current principal
   *
   * @return String
   * @throws WebdavException
   */
  public AccessPrincipal getPrincipal() throws WebdavException;

  /** Get a property handler
   *
   * @param ptype
   * @return PropertyHandler
   * @throws WebdavException
   */
  public PropertyHandler getPropertyHandler(PropertyType ptype) throws WebdavException;

  /**
   * @return UrlHandler object to manipulate urls.
   */
  public UrlHandler getUrlHandler();

  /* *
   * @return String url prefix derived from request.
   * /
  public String getUrlPrefix();

  /* *
   * @return boolean - true if using relative urls for broken clients
   * /
  public boolean getRelativeUrls();*/

  /** Does the value appear to represent a valid principal?
   *
   * @param val
   * @return true if it's a (possible) principal
   * @throws WebdavException
   */
  public boolean isPrincipal(String val) throws WebdavException;

  /** Return principal information for the given href. Also tests for a valid
   * principal.
   *
   *
   * @param href
   * @return PrincipalInfo
   * @throws WebdavException
   */
  public AccessPrincipal getPrincipal(String href) throws WebdavException;

  /**
   * @param id
   * @param whoType - from WhoDefs
   * @return String href
   * @throws WebdavException
   */
  public String makeHref(String id, int whoType) throws WebdavException;

  /** The urls should be principal urls. principalUrl can null for the current user.
   * The result is a collection of principal urls of which the given url is a
   * member, based upon rootUrl. For example, if rootUrl points to the base of
   * the user principal hierarchy, then the rsult should be at least the current
   * user's principal url, remembering that user principals are themselves groups
   * and the user is considered a member of their own group.
   *
   * @param rootUrl - url to base search on.
   * @param principalUrl - url of principal or null for current user
   * @return Collection of urls - always non-null
   * @throws WebdavException
   */
  public Collection<String>getGroups(String rootUrl,
                                     String principalUrl) throws WebdavException;

  /** Do we allow browsing of directories?
   *
   * @return boolean true if browsing disallowed
   * @throws WebdavException  for errors
   */
  public boolean getDirectoryBrowsingDisallowed() throws WebdavException;

  /** Given a calendar address return the associated calendar account.
   * For example, we might have a calendar address<br/>
   *   auser@ahost.org
   * <br/>with the associated account of <br/>
   * auser.<br/>
   *
   * <p>Whereever we need a user account use the converted value. Call
   * userToCaladdr for the inverse.
   *
   * @param caladdr      calendar address
   * @return AccessPrincipal or null if not caladdr for this system
   * @throws WebdavException  for errors
   */
  public AccessPrincipal caladdrToPrincipal(String caladdr) throws WebdavException;

  /** The inverse of caladdrToPrincipal
   *
   * @param principal
   * @return String calendar user address
   * @throws WebdavException
   */
  public String principalToCaladdr(AccessPrincipal principal) throws WebdavException;

  /** Given a valid AccessPrincipal return the associated calendar user information
   * needed for caldav interactions.
   *
   * @param principal     valid AccessPrincipal
   * @return CalUserInfo or null if not caladdr for this system
   * @throws WebdavException  for errors
   */
  public CalPrincipalInfo getCalPrincipalInfo(AccessPrincipal principal) throws WebdavException;

  /** Given a uri returns a Collection of uris that allow search operations on
   * principals for that resource.
   *
   * @param resourceUri
   * @return Collection of String
   * @throws WebdavException
   */
  public Collection<String> getPrincipalCollectionSet(String resourceUri)
         throws WebdavException;

  /** Given a PrincipalPropertySearch returns a Collection of matching principals.
   *
   * @param resourceUri
   * @param pps Collection of PrincipalPropertySearch
   * @return Collection of CalUserInfo
   * @throws WebdavException
   */
  public Collection<CalPrincipalInfo> getPrincipals(String resourceUri,
                                  PrincipalPropertySearch pps)
          throws WebdavException;

  /** Is href a valid principal?
   *
   * @param href
   * @return boolean true for a valid user
   * @throws WebdavException  for errors
   */
  public boolean validPrincipal(String href) throws WebdavException;

  /* ====================================================================
   *                   Scheduling
   * ==================================================================== */

  /** Return a set of hrefs for each resource affecting this users freebusy
   *
   * @return Collection of hrefs
   * @throws WebdavException  for errors
   */
  public Collection<String> getFreebusySet() throws WebdavException;

  /** Result for a single recipient.
   */
  public static class SchedRecipientResult implements ScheduleStates {
    /** */
    public String recipient;

    /** One of the above */
    public int status = scheduleUnprocessed;

    /** Set if this is the result of a freebusy request. */
    public CalDAVEvent freeBusy;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("ScheduleRecipientResult{");

      tsseg(sb, "", "recipient", recipient);
      tsseg(sb, ", ", "status", String.valueOf(status));

      sb.append("}");

      return sb.toString();
    }

    private static void tsseg(final StringBuilder sb, final String delim, final String name,
                              final String val) {
      sb.append(delim);
      sb.append(name);
      sb.append("=");
      sb.append(val);
    }
  }

  /** Request to schedule a meeting. The event object must have the organizer
   * and attendees and possibly recipients set. If no recipients are set, they
   * will be set from the attendees.
   *
   * <p>The functioning of this method must conform to the requirements of iTip.
   * The event object must have the required method (publish, request etc) set.
   *
   * <p>The event will be added to the users outbox which will trigger the send
   * of requests to other users inboxes. For users within this system the
   * request will be immediately addded to the recipients inbox. For external
   * users they are sent via mail.
   *
   * @param ev         Event object
   * @return ScheduleResult
   * @throws WebdavException
   */
  public Collection<SchedRecipientResult> schedule(CalDAVEvent ev)
                throws WebdavException;

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  /** Add an event/task/journal. If this is a scheduling event we are adding,
   * determined by examining the organizer and attendee properties, we will send
   * out invitations to the attendees, unless the noInvites flag is set.
   *
   * @param ev           CalDAVEvent object
   * @param noInvites    Set from request - if true don't send invites
   * @param rollbackOnError true if we rollback and throw an exception on error
   * @return Collection of overrides which did not match or null if all matched
   * @throws WebdavException
   */
 public Collection<CalDAVEvent> addEvent(CalDAVEvent ev,
                                         boolean noInvites,
                                         boolean rollbackOnError) throws WebdavException;

  /** Update an event/todo/journal.
   *
   * @param event         updated CalDAVEvent object
   * @throws WebdavException
   */
  public void updateEvent(CalDAVEvent event) throws WebdavException;

  /** Show the outcome of an update
   * @author douglm
   */
  public class UpdateResult {
    private boolean ok;

    private String reason;

    private static UpdateResult okResult = new UpdateResult();

    /**
     * @return result indicating OK.
     */
    public static UpdateResult getOkResult() {
      return okResult;
    }

    /**
     */
    private UpdateResult() {
      ok = true;
    }

    /**
     * @param reason
     */
    public UpdateResult(final String reason) {
      this.reason = reason;
    }

    /**
     * @return True for an OK update
     */
    public boolean getOk() {
      return ok;
    }

    /**
     * @return Non-null if !ok
     */
    public String getReason() {
      return reason;
    }
  }

  /** Update the supplied event using the web services update message.
   *
   * @param event         updated CalDAVEvent object
   * @param updates       set of updates to be applied
   * @return UpdateResult
   * @throws WebdavException
   */
  public UpdateResult updateEvent(CalDAVEvent event,
                                  List<SelectElementType> updates) throws WebdavException;

  /** Return the events for the current user in the given collection using the
   * supplied filter. Stored freebusy objects are returned as BwEvent
   * objects with the appropriate entity type. If retrieveList is supplied only
   * those fields (and a few required fields) will be returned.
   *
   * <p>We flag the desired entity types.
   *
   * @param col
   * @param filter - if non-null defines a search filter
   * @param retrieveList List of properties to retrieve or null for a full event.
   * @param recurRetrieval How recurring event is returned.
   * @return Collection  populated event value objects
   * @throws WebdavException
   */
  public Collection<CalDAVEvent> getEvents(CalDAVCollection col,
                                           FilterBase filter,
                                           List<String> retrieveList,
                                           RetrievalMode recurRetrieval)
          throws WebdavException;

  /** Get events given the collection and String name. Return null for not
   * found. There should be only one event or none. For recurring, the
   * overrides and possibly the instances will be attached.
   *
   * @param col        CalDAVCollection object
   * @param val        String possible name
   * @param recurRetrieval
   * @return CalDAVEvent or null
   * @throws WebdavException
   */
  public CalDAVEvent getEvent(CalDAVCollection col, String val,
                              RetrievalMode recurRetrieval)
          throws WebdavException;

  /**
   * @param ev
   * @param scheduleReply - true if we want a schduling reply posted
   * @throws WebdavException
   */
  public void deleteEvent(CalDAVEvent ev,
                          boolean scheduleReply) throws WebdavException;

  /** Get the free busy for one or more principals based on the given VFREEBUSY
   * request.
   *
   * @param val    A representation of a scheduling freebusy request to be
   *               acted upon.
   * @return ScheduleResult
   * @throws WebdavException
   */
  public Collection<SchedRecipientResult> requestFreeBusy(CalDAVEvent val)
          throws WebdavException;

  /** Handle the special freebusy resquests, i.e. non-CalDAV
   *
   * @param cua
   * @param originator value of the Originator header
   * @param recipients values of Recipient headers
   * @param tr
   * @param wtr
   * @throws WebdavException
   */
  public void getSpecialFreeBusy(String cua,
                                 Set<String> recipients,
                                 String originator,
                                 TimeRange tr,
                                 Writer wtr) throws WebdavException;

  /** Generate a free busy object for the given time period which reflects
   * the state of the given collection.
   *
   * @param col
   * @param depth
   * @param timeRange
   * @return CalDAVEvent - as a freebusy entity
   * @throws WebdavException
   */
  public CalDAVEvent getFreeBusy(final CalDAVCollection col,
                                 final int depth,
                                 final TimeRange timeRange) throws WebdavException;

  /** Check the access for the given entity. Returns the current access
   * or null or optionally throws a no access exception.
   *
   * @param ent
   * @param desiredAccess
   * @param returnResult
   * @return CurrentAccess
   * @throws WebdavException if returnResult false and no access
   */
  public CurrentAccess checkAccess(WdEntity ent,
                                   int desiredAccess,
                                   boolean returnResult)
          throws WebdavException;

  /**
   * @param ev
   * @param acl
   * @throws WebdavException
   */
  public void updateAccess(CalDAVEvent ev,
                           Acl acl) throws WebdavException;

  /** Copy or move the given entity to the destination collection with the given name.
   * Status is set on return
   *
   * @param from      Source entity
   * @param to        Destination collection
   * @param name      String name of new entity
   * @param copy      true for copying
   * @param overwrite destination exists
   * @return true if destination created (i.e. not updated)
   * @throws WebdavException
   */
  public boolean copyMove(CalDAVEvent from,
                          CalDAVCollection to,
                          String name,
                          boolean copy,
                          boolean overwrite) throws WebdavException;

  /* ====================================================================
   *                   Collections
   * ==================================================================== */

  /** Return a new object representing the parameters. No collection is
   * created. makeCollection must be called subsequently with the object.
   *
   * @param isCalendarCollection
   * @param parentPath
   * @return CalDAVCollection
   * @throws WebdavException
   */
  public CalDAVCollection newCollectionObject(boolean isCalendarCollection,
                                              String parentPath) throws WebdavException;

  /**
   * @param col
   * @param acl
   * @throws WebdavException
   */
  public void updateAccess(CalDAVCollection col,
                           Acl acl) throws WebdavException;

  /**
   * @param col   Initialised collection object
   * @return int status
   * @throws WebdavException
   */
  public int makeCollection(CalDAVCollection col) throws WebdavException;

  /** Copy or move the collection to another location.
   * Status is set on return
   *
   * @param from      Source collection
   * @param to        Destination collection
   * @param copy      true for copying
   * @param overwrite destination exists
   * @throws WebdavException
   */
  public void copyMove(CalDAVCollection from,
                       CalDAVCollection to,
                       boolean copy,
                       boolean overwrite) throws WebdavException;

  /** Get a collection given the path
   *
   * @param  path     String path of calendar
   * @return CalDAVCollection null for unknown collection
   * @throws WebdavException
   */
  public CalDAVCollection getCollection(String path) throws WebdavException;

  /** Update a collection.
   *
   * @param val           updated CalDAVCollection object
   * @throws WebdavException
   */
  public void updateCollection(CalDAVCollection val) throws WebdavException;

  /**
   * @param col
   * @throws WebdavException
   */
  public void deleteCollection(CalDAVCollection col) throws WebdavException;

  /** Returns children of the given collection to which the current user has
   * some access.
   *
   * @param  col          parent collection
   * @return Collection   of CalDAVCollection
   * @throws WebdavException
   */
  public Collection<CalDAVCollection> getCollections(CalDAVCollection col)
          throws WebdavException;

  /** If the parameter is an alias and the target has not been resolved it will
   * be fetched and implanted in the parameter object.
   *
   * @param col
   * @throws WebdavException
   */
  public void resolveAlias(CalDAVCollection col) throws WebdavException;

  /* ====================================================================
   *                   Files
   * ==================================================================== */

  /** Return a new object representing the parameters. No resource is
   * created. putFile must be called subsequently with the object.
   *
   * @param parentPath
   * @return CalDAVResource
   * @throws WebdavException
   */
  public CalDAVResource newResourceObject(String parentPath) throws WebdavException;

  /** PUT a file.
   *
   * @param coll         CalDAVCollection defining recipient collection
   * @param val          CalDAVResource
   * @throws WebdavException
   */
  public void putFile(CalDAVCollection coll,
                      CalDAVResource val) throws WebdavException;

  /** GET a file.
   *
   * @param coll         CalDAVCollection containing file
   * @param name
   * @return CalDAVResource
   * @throws WebdavException
   */
  public CalDAVResource getFile(CalDAVCollection coll,
                            String name) throws WebdavException;

  /** Get resource content given the resource. It will be set in the resource
   * object
   *
   * @param  val CalDAVResource
   * @throws WebdavException
   */
  public void getFileContent(CalDAVResource val) throws WebdavException;

  /** Get the files in a collection.
   *
   * @param coll         CalDAVCollection containing file
   * @return Collection of CalDAVResource
   * @throws WebdavException
   */
  public Collection<CalDAVResource> getFiles(CalDAVCollection coll) throws WebdavException;

  /** Update a file.
   *
   * @param val          CalDAVResource
   * @param updateContent if true we also update the content
   * @throws WebdavException
   */
  public void updateFile(CalDAVResource val,
                         boolean updateContent) throws WebdavException;

  /** Delete a file.
   *
   * @param val          CalDAVResource
   * @throws WebdavException
   */
  public void deleteFile(CalDAVResource val) throws WebdavException;

  /** Copy or move the given file to the destination collection with the given name.
   * Status is set on return
   *
   * @param from      Source resource
   * @param toPath    Destination collection path
   * @param name      String name of new entity
   * @param copy      true for copying
   * @param overwrite destination exists
   * @return true if destination created (i.e. not updated)
   * @throws WebdavException
   */
  public boolean copyMoveFile(CalDAVResource from,
                              String toPath,
                              String name,
                              boolean copy,
                              boolean overwrite) throws WebdavException;

  /** Make an ical Calendar from an event.
   *
   * @param ev
   * @param incSchedMethod - true if we should emit the scheduling method
   * @return Calendar
   * @throws WebdavException
   */
  public Calendar toCalendar(CalDAVEvent ev,
                             boolean incSchedMethod) throws WebdavException;

  /** Make an XML IcalendarType from an event.
   *
   * @param ev
   * @param incSchedMethod - true if we should emit the scheduling method
   * @param pattern - non-null to restrict returned properties
   * @return IcalendarType
   * @throws WebdavException
   */
  public IcalendarType toIcalendar(CalDAVEvent ev,
                               boolean incSchedMethod,
                               IcalendarType pattern) throws WebdavException;

  /** Convert a Calendar to it's string form
   *
   * @param cal Calendar to convert
   * @return String representation
   * @throws WebdavException
   */
  public String toIcalString(Calendar cal) throws WebdavException;

  /** What method do we want emitted */
  public static enum MethodEmitted {
    /** No method for calendar */
    noMethod,

    /** Method from event */
    eventMethod,

    /** It's a publish */
    publish
  }

  /** Write a collection of events as an ical calendar.
   *
   * @param evs
   * @param method - what scheduling method?
   * @param xml - if this is embedded in an xml stream
   * @param wtr - if standalone output or no xml stream initialized.
   * @param contentType
   * @throws WebdavException
   */
  public void writeCalendar(Collection<CalDAVEvent> evs,
                            MethodEmitted method,
                            XmlEmit xml,
                            Writer wtr,
                            String contentType) throws WebdavException;

  /** Expected result type */
  public enum IcalResultType {
    /** Expect one (non-timezone) component only */
    OneComponent,

    /** Expect one timezone only */
    TimeZone
  }

  /** Convert the Icalendar reader to a Collection of Calendar objects
   *
   * @param col       collection in which to place entities
   * @param rdr
   * @param contentType  null for ICalendar or valid calendar mime type
   * @param rtype
   * @param mergeAttendees True if we should only update our own attendee.
   * @return SysiIcalendar
   * @throws WebdavException
   */
  public SysiIcalendar fromIcal(CalDAVCollection col,
                                Reader rdr,
                                String contentType,
                                IcalResultType rtype,
                                boolean mergeAttendees) throws WebdavException;

  /** Convert the Icalendar object to a Collection of Calendar objects
   *
   * @param col       collection in which to place entities
   * @param ical
   * @param rtype
   * @return SysiIcalendar
   * @throws WebdavException
   */
  public SysiIcalendar fromIcal(CalDAVCollection col,
                                final IcalendarType ical,
                                IcalResultType rtype) throws WebdavException;

  /** Create a Calendar object from the named timezone and convert to
   * a String representation
   *
   * @param tzid       String timezone id
   * @return String
   * @throws WebdavException
   */
  public String toStringTzCalendar(String tzid) throws WebdavException;

  /** Given a timezone spec return the tzid
   *
   * @param val
   * @return String tzid or null for failure
   * @throws WebdavException
   */
  public String tzidFromTzdef(String val) throws WebdavException;

  /** Max size for an entity
   *
   * @return int
   * @throws WebdavException
   */
  public int getMaxUserEntitySize() throws WebdavException;

  /** Called on the way out before close if there was an error.
   *
   */
  public void rollback();

  /** End any transactions.
   *
   * @throws WebdavException
   */
  public void close() throws WebdavException;
}
