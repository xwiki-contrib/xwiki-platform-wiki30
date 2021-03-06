1. Building order:
------------------

xwiki-platform-gwt-dom
xwiki-platform-gwt-dom-mutation
xwiki-platform-gwt-user
xwiki-platform-wysiwyg-plugin-api
xwiki-platform-wysiwyg-plugin-mutation
xwiki-platform-wysiwyg-client
xwiki-platform-wysiwyg-server
xwiki-platform-wysiwyg-war

Skip the modules that you haven't modified. For development, use -Pdev maven profile when building
xwiki-platform-wysiwyg-war to reduce the GWT compilation time; only the JavaScript permutations for the default language
will be generated.


2. Updating the WYSIWYG editor of your XE instance:
---------------------------------------------------

The WYSIWYG editor version and the XE version must be kept synchronized so you should use/modify the version of the
WYSIWYG editor that matches your XE version.

a) Remove resources/js/xwiki/wysiwyg/xwe folder from your XE instance and copy the one from the war generated by the
xwiki-platform-wysiwyg-war module. Don't overwrite! Delete + Copy.

b) If you changed the server side or you modified interfaces shared between the client side and the server side then you
need to also overwrite the jars from WEB-INF/lib with the two jars from the war generated by xwiki-platform-wysiwyg-war
module.

c) If you did b) then restart XE, otherwise just clear the browser cache.


3. WYSIWYG editor plugins:
--------------------------

You need to enable WYSIWYG editor plugins from the administration section. They are not loaded/detected by default.