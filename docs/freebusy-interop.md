Requirement
-----------

In many cases ZCS is deployed in an environment where there is a mix of
Zimbra server and 3rd party servers.  In such environment, typically the users
are partitioned into a group using Zimbra server, and a group using 3rd party
servers.  When a user wants to schedule a meeting with other users, the 
free/busy schedule of the other users is readily available only if 
the attendees are in the same group as the organizer.  

Some calendaring software allows limited interop with 3rd party systems.  
For example Outlook allows downloading free/busy schedule for a user from 
a web server.  However, configuring URL for each attendees can be a tedious 
task.  It would be desirable if Zimbra server can provide server to server 
free/busy sync with 3rd party servers.  Then the Zimbra users will be able to 
query the free/busy schedule of users on other system, such as Exchange 
or Domino server, the same way they would query the Zimbra users.  

Zimbra should also support pushing free/busy schedule of Zimbra users to the 
3rd party server, if the 3rd party server supports such features.

Free/busy Provider Framework
----------------------------

Zimbra provides a framework where the interop with any 3rd party system
can be added via an extension.  Through the extension, Zimbra server can
query the free/busy schedule of users on 3rd party system, and also
propagate free/busy schedule of a Zimbra user to the 3rd party system.
The mechanism is described later in the document.

Interop with Exchange 2003
--------------------------

Zimbra server has a default implementation of Exchange free busy provider.
Zimbra server can pull the free/busy schedule of a user on Exchange, and
also push the free/busy schedule of a Zimbra user to Exchange server.  The
retrieval of free/busy (Exchange to Zimbra) is done via REST interface
on the Exchange server.  The propagation of free/busy (Zimbra to Exchange)
is done via WebDAV interface on the Exchange server.  In both cases, Zimbra
needs to authenticate to the Exchange server via HTTP basic authentication
or HTML form based authentication ala OWA.

In order to enable the default Exchange free busy interop implementation, 
the following conditions need to be met.

1.  There should be a single AD in the system or Global Catalog is available.
2.  Zimbra server can access HTTP(S) port of IIS on at least one of the 
    Exchange server.
3.  The web interface to Exchange public folders (http://server/public/) needs
    to be available via IIS.
4.  Zimbra users need to be provisioned as a contact on Active Directory using
    the same administrative group for each mail domain.
5.  The Exchange username must be provisioned in the account attribute
    zimbraForeignPrincipal for all the Zimbra users.
    
The condition #4 and #5 are required only for Zimbra -> Exchange free/busy 
replication.  With the first three condition met, Exchange -> Zimbra free/busy 
lookup is still possible.

After creating the contacts, `legacyExchangeDN` attribute can be verified by 
running ADSI Edit tool, open the contact object.  Search for the attribute 
`legacyExchangeDN` for the contact.  For example the attribute value would be

    legacyExchangeDN : /o=First Organization/ou=First Administrative Group/cn=Recipients/cn=james

The substring left and right of `/cn=Recipients` are important, and need to
be consistent across the Zimbra contacts.  The left part 
`/o=First Organization/ou=First Administrative Group` corresponds to the domain
attribute `zimbraFreebusyExchangeUserOrg`.  The value of "cn" corresponds to the
Exchange account name specified in zimbraForeignPrincipal.

There are five global config attributes to enable the interop with Exchange server.

globalConfig:

  - zimbraFreebusyExchangeAuthUsername
  - zimbraFreebusyExchangeAuthPassword
  - zimbraFreebusyExchangeAuthScheme
  - zimbraFreebusyExchangeURL
  - zimbraFreebusyExchangeUserOrg

`zimbraFreebusyExchangeAuthUsername` and `zimbraFreebusyExchangeAuthPassword` are
used to authenticate against Exchange server on REST and WebDAV interface.
zimbraFreebusyExchangeAuthScheme can be set to 'basic' or 'form', depending
on how Exchange server is configured.  When set to basic, Zimbra will
attempt HTTP basic authentication.  When set to 'form', it will POST
HTML form to /exchweb/bin/auth/owaauth.dll to get the auth Cookie set.
`zimbraFreebusyExchangeURL` should be set to the URL of the Exchange server.
`zimbraFreebusyExchangeUserOrg` is the first part of the administrative group.
Typically they are set to `/o=First Organization/ou=First Administrative Group`.
Note that the value Org is from legacyExchangeDN attribute of the user,
not the DN of the user object.

Use zmprov tool to set the global config variables.  For example, assuming
the user zimbra exists on the Exchange server on exchange.mycompany.com
with password 'changeme' :

    zmprov mcf zimbraFreebusyExchangeAuthUsername zimbra
    zmprov mcf zimbraFreebusyExchangeAuthPassword changeme
    zmprov mcf zimbraFreebusyExchangeAuthScheme basic
    zmprov mcf zimbraFreebusyExchangeURL http://exchange.mycompany.com
    zmprov mcf zimbraFreebusyExchangeUserOrg "/o=First Organization/ou=First Administrative Group"

Add the Zimbra account to Exchange account mapping to `zimbraForeignPrincipal`
attribute.  Note that `zimbraForeignPrincipal` is a multi value attribute.  Be
careful not to wipe out the existing values when adding a new one.  Use the
prefix "ad:" in front of the Exchange account username to indicate the usage
for Active Directory.

    zmprov ma james@mycompany.com zimbraForeignPrincipal ad:james

Each of the config variables above can be overridden in the domain level.

    zmprov md anotherdomain.com zimbraFreebusyExchangeUserOrg "/o=AnotherOrg/ou=First Administrative Group"

Complex Exchange environment
--------------------------

If the Exchange environment does not meet the requirement for above, 
the default Exchange provider needs to be extended to work in 
such environment.  Zimbra provides a Java interface

    com.zimbra.cs.fb.ExchangeFreeBusyProvider.ExchangeResolver

The interface requires one method

    public ServerInfo getServerInfo(String emailAddr);

The resolver implementation should take an email address of a user on AD,
then return ServerInfo object.  If the user is not found in AD it should
return null.  ServerInfo is defined as

    public static class ServerInfo {
        public String url;
        public String org;
        public String cn;
        public String authUsername;
        public String authPassword;
        public AuthScheme scheme;  // basic or form
    }

Finally, the bootstrapping of the exchange provider can be done via
`ZimbraExtension`.  Following is an example to illustrate how this
can be implemented.

    public class FbExtension implements ZimbraExtension {
        public void init() {
            ExchangeFreeBusyProvider.registerResolver(new ExchangeUserResolver() {
                public ServerInfo getServerInfo(String emailAddr) {
                    ServerInfo info = new ServerInfo();
                    info.url = "http://exchange.mycompany.com";
                    info.authUsername = "zimbra";
                    info.authPassword = "changeme";
                    info.scheme = AuthScheme.basic;
                    info.org = "/o=First Organization/ou=First Administrative Group";
                    info.cn = emailAddr;
                    if (emailAddr.indexOf('@') > 0)
                        info.cn = emailAddr.substring(0, emailAddr.indexOf('@'));
                    return info;
                }
            }, 0);
        }
    
        public void destroy() {
        }
        
        public String getName() {
            return "ExtensionFbProvider";
        }
    }


Interop with other non-Exchange systems
---------------------------------------

````
NOTE:

THIS FEATURE IS NOT OFFICIALLY SUPPORTED.  THIS SECTION IS PRIMARILY FOR FYI.
FREE/BUSY INTEROP HAS BEEN TESTED AGAINST EXCHANGE 2003 AND 2007 ONLY.
````

Zimbra can handle other 3rd party system by extending the `FreeBusyProvider`
framework.  

    public abstract class FreeBusyProvider {
        public abstract FreeBusyProvider getInstance();
        public abstract String getName();
        
        // free/busy lookup from 3rd party system
        public abstract void addFreeBusyRequest(Request req) throws FreeBusyUserNotFoundException;
        public abstract List<FreeBusy> getResults();
        
        // propagation of Zimbra users free/busy to 3rd party system
        public abstract boolean registerForMailboxChanges();
        public abstract boolean handleMailboxChange(String accountId);
        public abstract long cachedFreeBusyStartTime();
        public abstract long cachedFreeBusyEndTime();
        public abstract String foreignPrincipalPrefix();
    }

The API allows batching of lookup requests.  The framework invokes 
`addFreeBusyRequest()` for each non-Zimbra users.  The provider implementation
should throw `FreeBusyUserNotFoundException` when the user is not found.
This allows for multiple `FreeBusyProvider`s to co-exist in the system.
The framework will then call `getResults()`, and at that point the provider
returns a set of FreeBusy for the batched requests.

If the provider can handle propagation of Zimbra users free/busy,
it should declare it so by returning true on `registerForMailboxChanges()`.
Then the framework will call `handleMailboxChange()` for each providers
when a change in the mailbox that could result in recalculation of free/busy
is detected. `cachedFreeBusyStartTime()` and `cachedFreeBusyEndTime()` are
millis since epoch to indicate the interval of free/busy that can be cached 
in the 3rd party system.  For example the basic exchange free/busy provider
caches free/busy from the beginning of the month, for two months.
