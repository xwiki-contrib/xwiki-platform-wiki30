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
package org.xwiki.gwt.wysiwyg.client.plugin.rt;

import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.xwiki.gwt.dom.client.DOMUtils;
import org.xwiki.gwt.dom.client.Range;
import org.xwiki.gwt.dom.client.Selection;
import org.xwiki.gwt.user.client.Config;
import org.xwiki.gwt.user.client.Console;
import org.xwiki.gwt.user.client.ui.rta.RichTextArea;
import org.xwiki.gwt.user.client.ui.rta.cmd.Command;
import org.xwiki.gwt.user.client.ui.rta.cmd.CommandListener;
import org.xwiki.gwt.user.client.ui.rta.cmd.CommandManager;
import org.xwiki.gwt.wysiwyg.client.plugin.internal.AbstractPlugin;

import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;

/**
 * Logs DOM mutations generated inside the rich text area.
 * 
 * @version $Id$
 */
public class RealTimePlugin extends AbstractPlugin implements KeyPressHandler, CommandListener
{
    /**
     * The list of command that shouldn't be broadcasted.
     */
    private static final List<Command> IGNORED_COMMANDS = Arrays.asList(Command.UPDATE, Command.ENABLE, new Command(
        "submit"));

    /**
     * The stack of operation calls created from rich text area commands, before the commands are executed. We need this
     * stack because we don't want to broadcast nested commands (in case a command triggers other commands).
     */
    private Stack<OperationCall> commandOperationCalls = new Stack<OperationCall>();

    /**
     * {@inheritDoc}
     * 
     * @see AbstractPlugin#init(RichTextArea, Config)
     */
    public void init(RichTextArea textArea, Config config)
    {
        super.init(textArea, config);

        saveRegistration(textArea.addKeyPressHandler(this));
        getTextArea().getCommandManager().addCommandListener(this);
    }

    /**
     * {@inheritDoc}
     * 
     * @see AbstractPlugin#destroy()
     */
    public void destroy()
    {
        getTextArea().getCommandManager().removeCommandListener(this);

        super.destroy();
    }

    /**
     * {@inheritDoc}
     * 
     * @see CommandListener#onBeforeCommand(CommandManager, Command, String)
     */
    public boolean onBeforeCommand(CommandManager sender, Command command, String param)
    {
        if (getTextArea().isAttached() && getTextArea().isEnabled()) {
            Selection selection = getTextArea().getDocument().getSelection();
            if (selection.getRangeCount() > 0) {
                Range range = selection.getRangeAt(0);
                commandOperationCalls.push(new OperationCall(command.toString(), param, getTarget(range)));
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * 
     * @see CommandListener#onCommand(CommandManager, Command, String)
     */
    public void onCommand(CommandManager sender, final Command command, final String param)
    {
        OperationCall operationCall = commandOperationCalls.pop();
        if (commandOperationCalls.isEmpty() && !IGNORED_COMMANDS.contains(command)) {
            broadcast(operationCall);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see KeyPressHandler#onKeyPress(KeyPressEvent)
     */
    public void onKeyPress(KeyPressEvent event)
    {
        boolean isAltControlOrMetaDown = event.isAltKeyDown() || event.isControlKeyDown() || event.isMetaKeyDown();
        if (getTextArea().isAttached() && getTextArea().isEnabled() && !isAltControlOrMetaDown) {
            Selection selection = getTextArea().getDocument().getSelection();
            if (selection.getRangeCount() > 0) {
                Range range = selection.getRangeAt(0);
                broadcast(new OperationCall("KeyPress", String.valueOf(event.getUnicodeCharCode()), getTarget(range)));
            }
        }
    }

    /**
     * Converts a DOM range to an operation target.
     * 
     * @param range a DOM range
     * @return the corresponding operation target
     */
    private OperationTarget getTarget(Range range)
    {
        OperationTarget target = new OperationTarget();
        target.setStartContainer(getLocator(range.getStartContainer()));
        target.setStartOffset(range.getStartOffset());
        target.setEndContainer(getLocator(range.getEndContainer()));
        target.setEndOffset(range.getEndOffset());
        return target;
    }

    /**
     * @param node a DOM node
     * @return a string locator for the given node relative to the {@code BODY} element of the edited HTML document
     */
    private String getLocator(Node node)
    {
        StringBuffer locator = new StringBuffer();
        Node ancestor = node;
        while (ancestor != null && ancestor != getTextArea().getDocument().getBody()) {
            if (locator.length() > 0) {
                locator.insert(0, '/');
            }
            locator.insert(0, DOMUtils.getInstance().getNodeIndex(ancestor));
            ancestor = ancestor.getParentNode();
        }
        return locator.toString();
    }

    /**
     * Broadcast an operation call.
     * 
     * @param operationCall the operation call to broadcast
     */
    private void broadcast(OperationCall operationCall)
    {
        JSONObject jsonTarget = new JSONObject();
        jsonTarget.put("startContainer", new JSONString(operationCall.getTarget().getStartContainer()));
        jsonTarget.put("startOffset", new JSONNumber(operationCall.getTarget().getStartOffset()));
        jsonTarget.put("endContainer", new JSONString(operationCall.getTarget().getEndContainer()));
        jsonTarget.put("endoffset", new JSONNumber(operationCall.getTarget().getEndOffset()));

        JSONObject jsonOperationCall = new JSONObject();
        jsonOperationCall.put("operationId", new JSONString(operationCall.getOperationId()));
        jsonOperationCall.put("value", new JSONString(operationCall.getValue()));
        jsonOperationCall.put("target", jsonTarget);

        Console.getInstance().log(jsonOperationCall.toString());
    }
}
