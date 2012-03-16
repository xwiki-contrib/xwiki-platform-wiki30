/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.gwt.wysiwyg.client.plugin.link.rt;

import org.xwiki.gwt.wysiwyg.client.plugin.Plugin;
import org.xwiki.gwt.wysiwyg.client.plugin.internal.AbstractPluginFactory;
import org.xwiki.gwt.wysiwyg.client.wiki.WikiService;
import org.xwiki.gwt.wysiwyg.client.wiki.WikiServiceAsync;
import org.xwiki.gwt.wysiwyg.client.wiki.WikiServiceAsyncCacheProxy;

import com.google.gwt.core.client.GWT;

/**
 * Factory for {@link RTLinkPlugin}.
 *
 * @version $Id$
 */
public final class RTLinkPluginFactory extends AbstractPluginFactory
{
    /**
     * The singleton factory instance.
     */
    private static RTLinkPluginFactory instance;

    /**
     * The service used to access the wiki.
     */
    private final WikiServiceAsync wikiService =
        new WikiServiceAsyncCacheProxy((WikiServiceAsync) GWT.create(WikiService.class));

    /**
     * Default constructor.
     */
    private RTLinkPluginFactory()
    {
        super("rt-link");
    }

    /**
     * @return the singleton factory instance
     */
    public static synchronized RTLinkPluginFactory getInstance()
    {
        if (instance == null) {
            instance = new RTLinkPluginFactory();
        }
        return instance;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.xwiki.gwt.wysiwyg.client.plugin.internal.AbstractPluginFactory#newInstance()
     */
    public Plugin newInstance()
    {
        return new RTLinkPlugin(wikiService);
    }
}
