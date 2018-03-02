/**
 * Replace all backreferences in the to pattern with the matched
 * groups of the source.
 * @param source the source file name.
 * @return the translated file name.
 */
protected String replaceReferences(String source) {
    Vector v = reg.getGroups(source, regexpOptions);

    result.setLength(0);
    for (int i = 0; i < to.length; i++) {
        if (to[i] == '\\') {
            if (++i < to.length) {
                int value = Character.digit(to[i], 10);
                if (value > -1) {
                    result.append((String) v.elementAt(value));
                } else {
                    result.append(to[i]);
                }
            } else {
                // XXX - should throw an exception instead?
                result.append('\\');
            }
        } else {
            result.append(to[i]);
        }
    }
    return result.substring(0);
}

///===///

/**
 * Tests whether or not the parent classloader should be checked for
 * a resource before this one. If the resource matches both the
 * "use parent classloader first" and the "use this classloader first"
 * lists, the latter takes priority.
 *
 * @param resourceName The name of the resource to check.
 *                     Must not be <code>null</code>.
 *
 * @return whether or not the parent classloader should be checked for a
 *         resource before this one is.
 */
private boolean isParentFirst(String resourceName) {
    // default to the global setting and then see
    // if this class belongs to a package which has been
    // designated to use a specific loader first
    // (this one or the parent one)

    // XXX - shouldn't this always return false in isolated mode?

    boolean useParentFirst = parentFirst;

    for (Enumeration e = systemPackages.elements(); e.hasMoreElements();) {
        String packageName = (String) e.nextElement();
        if (resourceName.startsWith(packageName)) {
            useParentFirst = true;
            break;
        }
    }

    for (Enumeration e = loaderPackages.elements(); e.hasMoreElements();) {
        String packageName = (String) e.nextElement();
        if (resourceName.startsWith(packageName)) {
            useParentFirst = false;
            break;
        }
    }

    return useParentFirst;
}

///===///

/**
 * Call org.apache.env.Which if available
 * @param out the stream to print the content to.
 */
private static void doReportWhich(PrintStream out) {
    Throwable error = null;
    try {
        Class which = Class.forName("org.apache.env.Which");
        Method method
            = which.getMethod("main", new Class[]{String[].class});
        method.invoke(null, new Object[]{new String[]{}});
    } catch (ClassNotFoundException e) {
        out.println("Not available.");
        out.println("Download it at http://xml.apache.org/commons/");
    } catch (InvocationTargetException e) {
        error = e.getTargetException() == null ? e : e.getTargetException();
    } catch (Throwable e) {
        error = e;
    }
    // report error if something weird happens...this is diagnostic.
    if (error != null) {
        out.println("Error while running org.apache.env.Which");
        error.printStackTrace();
    }
    }

///===///

/**
 * Our execute method.
 * @return true if successful
 * @throws BuildException on error
 */
public boolean execute()
    throws BuildException {
    getJspc().log("Using jasper compiler", Project.MSG_VERBOSE);
    CommandlineJava cmd = setupJasperCommand();

    try {
        // Create an instance of the compiler, redirecting output to
        // the project log
        Java java = new Java(owner);
        Path p = getClasspath();
        if (getJspc().getClasspath() != null) {
            getProject().log("using user supplied classpath: " + p,
                             Project.MSG_DEBUG);
        } else {
            getProject().log("using system classpath: " + p,
                             Project.MSG_DEBUG);
        }
        java.setClasspath(p);
        java.setDir(getProject().getBaseDir());
        java.setClassname("org.apache.jasper.JspC");
        //this is really irritating; we need a way to set stuff
        String []args = cmd.getJavaCommand().getArguments();
        for (int i = 0; i < args.length; i++) {
            java.createArg().setValue(args[i]);
        }
        java.setFailonerror(getJspc().getFailonerror());
        //we are forking here to be sure that if JspC calls
        //System.exit() it doesn't halt the build
        java.setFork(true);
        java.setTaskName("jasperc");
        java.execute();
        return true;
    } catch (Exception ex) {
        if (ex instanceof BuildException) {
            throw (BuildException) ex;
        } else {
            throw new BuildException("Error running jsp compiler: ",
                                     ex, getJspc().getLocation());
        }
    } finally {
        getJspc().deleteEmptyJavaFiles();
    }
}

///===///other c

/**
 * Write SPI Information to JAR
 */
private void writeServices(ZipOutputStream zOut) throws IOException {
    Iterator serviceIterator;
    Service service;

    serviceIterator = serviceList.iterator();
    while (serviceIterator.hasNext()) {
       service = (Service) serviceIterator.next();
       //stolen from writeManifest
       super.zipFile(service.getAsStream(), zOut,
                     "META-INF/service/" + service.getType(),
                     System.currentTimeMillis(), null,
                     ZipFileSet.DEFAULT_FILE_MODE);
    }
}

///===///

/**
 * map from a jsp file to a base name; does not deal with extensions
 *
 * @param jspFile jspFile file
 * @return exensionless potentially remapped name
 */
private String mapJspToBaseName(File jspFile) {
    String className;
    className = stripExtension(jspFile);

    // since we don't mangle extensions like the servlet does,
    // we need to check for keywords as class names
    for (int i = 0; i < keywords.length; ++i) {
        if (className.equals(keywords[i])) {
            className += "%";
            break;
        }
    }

    // Fix for invalid characters. If you think of more add to the list.
    StringBuffer modifiedClassName = new StringBuffer(className.length());
    // first char is more restrictive than the rest
    char firstChar = className.charAt(0);
    if (Character.isJavaIdentifierStart(firstChar)) {
        modifiedClassName.append(firstChar);
    } else {
        modifiedClassName.append(mangleChar(firstChar));
    }
    // this is the rest
    for (int i = 1; i < className.length(); i++) {
        char subChar = className.charAt(i);
        if (Character.isJavaIdentifierPart(subChar)) {
            modifiedClassName.append(subChar);
        } else {
            modifiedClassName.append(mangleChar(subChar));
        }
    }
    return modifiedClassName.toString();
}

///===///

// not used, but public so theoretically must remain for BC?
public void assertEqualContent(File expect, File result)
    throws AssertionFailedError, IOException {
    if (!result.exists()) {
        fail("Expected file "+result+" doesn\'t exist");
    }

    InputStream inExpect = null;
    InputStream inResult = null;
    try {
        inExpect = new BufferedInputStream(new FileInputStream(expect));
        inResult = new BufferedInputStream(new FileInputStream(result));

        int expectedByte = inExpect.read();
        while (expectedByte != -1) {
            assertEquals(expectedByte, inResult.read());
            expectedByte = inExpect.read();
        }
        assertEquals("End of file", -1, inResult.read());
    } finally {
        if (inResult != null) {
            inResult.close();
        }
        if (inExpect != null) {
            inExpect.close();
        }
    }
}
///===///


/**
 * Concatenates a class path in the order specified by the
 * ${build.sysclasspath} property - using the supplied value if
 * ${build.sysclasspath} has not been set.
 */
private Path concatSpecialPath(String defValue, Path p) {
    Path result = new Path(getProject());

    String order = defValue;
    if (getProject() != null) {
        String o = getProject().getProperty("build.sysclasspath");
        if (o != null) {
            order = o;
        }
    }
    if (order.equals("only")) {
        // only: the developer knows what (s)he is doing
        result.addExisting(p, true);

    } else if (order.equals("first")) {
        // first: developer could use a little help
        result.addExisting(p, true);
        result.addExisting(this);

    } else if (order.equals("ignore")) {
        // ignore: don't trust anyone
        result.addExisting(this);

    } else {
        // last: don't trust the developer
        if (!order.equals("last")) {
            log("invalid value for build.sysclasspath: " + order,
                Project.MSG_WARN);
        }
        result.addExisting(this);
        result.addExisting(p, true);
    }
    return result;
}

///===///

        public void keyPressed(KeyEvent evt)
        {
            if(evt.getKeyCode() == KeyEvent.VK_ENTER)
            {
                goToSelectedNode();

                // fuck me dead
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        resultTree.requestFocus();
                    }
                });

                evt.consume();
            }
        }

///===///

//{{{ loadCaretInfo() method
    /**
     * Loads the caret information from the current buffer.
     * @since jEdit 2.5pre2
     */
    public void loadCaretInfo()
    {
        Integer caret = (Integer)buffer.getProperty(Buffer.CARET);
        //Selection[] selection = (Selection[])buffer.getProperty(Buffer.SELECTION);

        Integer firstLine = (Integer)buffer.getProperty(Buffer.SCROLL_VERT);
        Integer horizontalOffset = (Integer)buffer.getProperty(Buffer.SCROLL_HORIZ);

        if(caret != null)
        {
            textArea.setCaretPosition(Math.min(caret.intValue(),
                buffer.getLength()));
        }

        /*if(selection != null)
            textArea.setSelection(selection);*/

        if(firstLine != null)
            textArea.setFirstPhysicalLine(firstLine.intValue());

        if(horizontalOffset != null)
            textArea.setHorizontalOffset(horizontalOffset.intValue());

        /* Silly bug workaround #8694. If you look at the above code,
         * note that we restore the saved caret position first, then
         * scroll to the saved location. However, the caret changing
         * can itself result in scrolling to a different location than
         * what was saved; and since moveCaretPosition() calls
         * updateBracketHighlight(), the bracket highlight's out of
         * bounds calculation will rely on a different set of physical
         * first/last lines than what we will end up with eventually.
         * Instead of confusing the user with status messages that
         * appear at random when switching buffers, we simply hide the
         * message altogether. */
        view.getStatus().setMessage(null);
    } //}}}

///===///

    //{{{ replace() method
    /**
     * Replaces the current selection with the replacement string.
     * @param view The view
     * @return True if the operation was successful, false otherwise
     */
    public static boolean replace(View view)
    {
        // component that will parent any dialog boxes
        Component comp = SearchDialog.getSearchDialog(view);
        if(comp == null)
            comp = view;

        JEditTextArea textArea = view.getTextArea();

        Buffer buffer = view.getBuffer();
        if(!buffer.isEditable())
            return false;

        boolean smartCaseReplace = getSmartCaseReplace();

        Selection[] selection = textArea.getSelection();
        if(selection.length == 0)
        {
            view.getToolkit().beep();
            return false;
        }

        record(view,"replace(view)",true,false);

        // a little hack for reverse replace and find
        int caret = textArea.getCaretPosition();
        Selection s = textArea.getSelectionAtOffset(caret);
        if(s != null)
            caret = s.getStart();

        try
        {
            buffer.beginCompoundEdit();

            SearchMatcher matcher = getSearchMatcher();
            if(matcher == null)
                return false;

            initReplace();

            int retVal = 0;

            for(int i = 0; i < selection.length; i++)
            {
                s = selection[i];

                retVal += replaceInSelection(textArea,
                    buffer,matcher,smartCaseReplace,s);
            }

            boolean _reverse = !regexp && reverse && fileset instanceof CurrentBufferSet;
            if(_reverse)
            {
                // so that Replace and Find continues from
                // the right location
                textArea.moveCaretPosition(caret);
            }
            else
            {
                s = textArea.getSelectionAtOffset(
                    textArea.getCaretPosition());
                if(s != null)
                    textArea.moveCaretPosition(s.getEnd());
            }

            if(retVal == 0)
            {
                view.getToolkit().beep();
                return false;
            }

            return true;
        }
        catch(Exception e)
        {
            handleError(comp,e);
        }
        finally
        {
            buffer.endCompoundEdit();
        }

        return false;
    } //}}}

///===///

//{{{ processKeyEvent() method
    public void processKeyEvent(KeyEvent evt)
    {
        if(evt.getID() == KeyEvent.KEY_PRESSED)
        {
            VFSDirectoryEntryTableModel model =
                (VFSDirectoryEntryTableModel)getModel();
            int row = getSelectedRow();

            switch(evt.getKeyCode())
            {
            case KeyEvent.VK_LEFT:
                evt.consume();
                if(row != -1)
                {
                    if(model.files[row].expanded)
                    {
                        model.collapse(
                            VFSManager.getVFSForPath(
                            model.files[row].dirEntry.path),
                            row);
                        break;
                    }

                    for(int i = row - 1; i >= 0; i--)
                    {
                        if(model.files[i].expanded)
                        {
                            setSelectedRow(i);
                            break;
                        }
                    }
                }

                String dir = browserView.getBrowser()
                    .getDirectory();
                dir = MiscUtilities.getParentOfPath(dir);
                browserView.getBrowser().setDirectory(dir);
                break;
            case KeyEvent.VK_RIGHT:
                if(row != -1)
                {
                    if(!model.files[row].expanded)
                        toggleExpanded(row);
                }
                evt.consume();
                break;
            case KeyEvent.VK_DOWN:
                // stupid Swing
                if(row == -1 && getModel().getRowCount() != 0)
                {
                    setSelectedRow(0);
                    evt.consume();
                }
                break;
            case KeyEvent.VK_ENTER:
                browserView.getBrowser().filesActivated(
                    (evt.isShiftDown()
                    ? VFSBrowser.M_OPEN_NEW_VIEW
                    : VFSBrowser.M_OPEN),false);
                evt.consume();
                break;
            }
        }
        else if(evt.getID() == KeyEvent.KEY_TYPED)
        {
            if(evt.isControlDown() || evt.isAltDown()
                || evt.isMetaDown())
            {
                return;
            }

            // hack...
            if(evt.isShiftDown() && evt.getKeyChar() == '\n')
                return;

            VFSBrowser browser = browserView.getBrowser();

            switch(evt.getKeyChar())
            {
            case '~':
                if(browser.getMode() == VFSBrowser.BROWSER)
                    browser.setDirectory(System.getProperty(
                        "user.home"));
                break;
            case '/':
                if(browser.getMode() == VFSBrowser.BROWSER)
                    browser.rootDirectory();
                break;
            case '-':
                if(browser.getMode() == VFSBrowser.BROWSER)
                {
                    browser.setDirectory(
                        browser.getView().getBuffer()
                        .getDirectory());
                }
                break;
            default:
                typeSelectBuffer.append(evt.getKeyChar());
                doTypeSelect(typeSelectBuffer.toString(),
                    browser.getMode() == VFSBrowser
                    .CHOOSE_DIRECTORY_DIALOG);

                timer.stop();
                timer.setInitialDelay(750);
                timer.setRepeats(false);
                timer.start();
                return;
            }
        }

        if(!evt.isConsumed())
            super.processKeyEvent(evt);
    } //}}}

///===///

//{{{ updateWrapSettings() method
    void updateWrapSettings()
    {
        String wrap = buffer.getStringProperty("wrap");
        softWrap = wrap.equals("soft");
        if(textArea.maxLineLen <= 0)
        {
            softWrap = false;
            wrapMargin = 0;
        }
        else
        {
            // stupidity
            char[] foo = new char[textArea.maxLineLen];
            for(int i = 0; i < foo.length; i++)
            {
                foo[i] = ' ';
            }
            TextAreaPainter painter = textArea.getPainter();
            wrapMargin = (int)painter.getFont().getStringBounds(
                foo,0,foo.length,
                painter.getFontRenderContext())
                .getWidth();
        }
    } //}}}

///===///

    //{{{ indexURL() method
    /**
     * Reads the specified HTML file and adds all words defined therein to the
     * index.
     * @param url The HTML file's URL
     */
    public void indexURL(String url) throws Exception
    {
        InputStream _in;

        if(MiscUtilities.isURL(url))
            _in =  new URL(url).openStream();
        else
        {
            _in = new FileInputStream(url);
            // hack since HelpViewer needs a URL...
            url = "file:" + url;
        }

        indexStream(_in,url);
    } //}}}

///===///

public void keyPressed(KeyEvent evt)
        {
            if(evt.isConsumed())
                return;

            if(evt.getKeyCode() == KeyEvent.VK_ENTER)
            {
                // crusty workaround
                Component comp = getFocusOwner();
                while(comp != null)
                {
                    if(comp instanceof JComboBox)
                    {
                        JComboBox combo = (JComboBox)comp;
                        if(combo.isEditable())
                        {
                            Object selected = combo.getEditor().getItem();
                            if(selected != null)
                                combo.setSelectedItem(selected);
                        }
                        break;
                    }

                    comp = comp.getParent();
                }

                ok();
                evt.consume();
            }
            else if(evt.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                cancel();
                evt.consume();
            }
        }

///===///

//{{{ goToSelectedNode() method
    private void goToSelectedNode()
    {
        TreePath path = resultTree.getSelectionPath();
        if(path == null)
            return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path
            .getLastPathComponent();
        Object value = node.getUserObject();

        if(node.getParent() == resultTreeRoot)
        {
            // do nothing if clicked "foo (showing n occurrences in m files)"
        }
        else if(value instanceof String)
        {
            Buffer buffer = jEdit.openFile(view,(String)value);
            if(buffer == null)
                return;

            view.goToBuffer(buffer);

            // fuck me dead
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    resultTree.requestFocus();
                }
            });
        }
        else if (value instanceof HyperSearchResult)
        {
            ((HyperSearchResult)value).goTo(view);
        }
    } //}}}

///===///

//{{{ handleRule() method
    /**
     * Checks if the rule matches the line at the current position
     * and handles the rule if it does match
     */
    private boolean handleRule(ParserRule checkRule, boolean end)
    {
        //{{{ Some rules can only match in certain locations
        if(!end)
        {
            if(Character.toUpperCase(checkRule.hashChar)
                != Character.toUpperCase(line.array[pos]))
            {
                return false;
            }
        }

        int offset = ((checkRule.action & ParserRule.MARK_PREVIOUS) != 0) ?
            lastOffset : pos;
        int posMatch = (end ? checkRule.endPosMatch : checkRule.startPosMatch);

        if((posMatch & ParserRule.AT_LINE_START)
            == ParserRule.AT_LINE_START)
        {
            if(offset != line.offset)
                return false;
        }
        else if((posMatch & ParserRule.AT_WHITESPACE_END)
            == ParserRule.AT_WHITESPACE_END)
        {
            if(offset != whitespaceEnd)
                return false;
        }
        else if((posMatch & ParserRule.AT_WORD_START)
            == ParserRule.AT_WORD_START)
        {
            if(offset != lastOffset)
                return false;
        } //}}}

        int matchedChars = 1;
        CharIndexedSegment charIndexed = null;
        REMatch match = null;

        //{{{ See if the rule's start or end sequence matches here
        if(!end || (checkRule.action & ParserRule.MARK_FOLLOWING) == 0)
        {
            // the end cannot be a regular expression
            if((checkRule.action & ParserRule.REGEXP) == 0 || end)
            {
                if(end)
                {
                    if(context.spanEndSubst != null)
                        pattern.array = context.spanEndSubst;
                    else
                        pattern.array = checkRule.end;
                }
                else
                    pattern.array = checkRule.start;
                pattern.offset = 0;
                pattern.count = pattern.array.length;
                matchedChars = pattern.count;

                if(!SyntaxUtilities.regionMatches(context.rules
                    .getIgnoreCase(),line,pos,pattern.array))
                {
                    return false;
                }
            }
            else
            {
                // note that all regexps start with \A so they only
                // match the start of the string
                int matchStart = pos - line.offset;
                charIndexed = new CharIndexedSegment(line,matchStart);
                match = checkRule.startRegexp.getMatch(
                    charIndexed,0,RE.REG_ANCHORINDEX);
                if(match == null)
                    return false;
                else if(match.getStartIndex() != 0)
                    throw new InternalError("Can't happen");
                else
                {
                    matchedChars = match.getEndIndex();
                    /* workaround for hang if match was
                     * zero-width. not sure if there is
                     * a better way to handle this */
                    if(matchedChars == 0)
                        matchedChars = 1;
                }
            }
        } //}}}

        //{{{ Check for an escape sequence
        if((checkRule.action & ParserRule.IS_ESCAPE) == ParserRule.IS_ESCAPE)
        {
            if(context.inRule != null)
                handleRule(context.inRule,true);

            escaped = !escaped;
            pos += pattern.count - 1;
        }
        else if(escaped)
        {
            escaped = false;
            pos += pattern.count - 1;
        } //}}}
        //{{{ Handle start of rule
        else if(!end)
        {
            if(context.inRule != null)
                handleRule(context.inRule,true);

            markKeyword((checkRule.action & ParserRule.MARK_PREVIOUS)
                != ParserRule.MARK_PREVIOUS);

            switch(checkRule.action & ParserRule.MAJOR_ACTIONS)
            {
            //{{{ SEQ
            case ParserRule.SEQ:
                context.spanEndSubst = null;

                if((checkRule.action & ParserRule.REGEXP) != 0)
                {
                    handleTokenWithSpaces(tokenHandler,
                        checkRule.token,
                        pos - line.offset,
                        matchedChars,
                        context);
                }
                else
                {
                    tokenHandler.handleToken(line,
                        checkRule.token,
                        pos - line.offset,
                        matchedChars,context);
                }

                // a DELEGATE attribute on a SEQ changes the
                // ruleset from the end of the SEQ onwards
                if(checkRule.delegate != null)
                {
                    context = new LineContext(
                        checkRule.delegate,
                        context.parent);
                    keywords = context.rules.getKeywords();
                }
                break;
            //}}}
            //{{{ SPAN, EOL_SPAN
            case ParserRule.SPAN:
            case ParserRule.EOL_SPAN:
                context.inRule = checkRule;

                byte tokenType = ((checkRule.action & ParserRule.EXCLUDE_MATCH)
                    == ParserRule.EXCLUDE_MATCH
                    ? context.rules.getDefault() : checkRule.token);

                if((checkRule.action & ParserRule.REGEXP) != 0)
                {
                    handleTokenWithSpaces(tokenHandler,
                        tokenType,
                        pos - line.offset,
                        matchedChars,
                        context);
                }
                else
                {
                    tokenHandler.handleToken(line,tokenType,
                        pos - line.offset,
                        matchedChars,context);
                }

                char[] spanEndSubst = null;
                /* substitute result of matching the rule start
                 * into the end string.
                 *
                 * eg, in shell script mode, <<\s*(\w+) is
                 * matched into \<$1\> to construct rules for
                 * highlighting read-ins like this <<EOF
                 * ...
                 * EOF
                 */
                if(charIndexed != null && checkRule.end != null)
                {
                    spanEndSubst = substitute(match,
                        checkRule.end);
                }

                context.spanEndSubst = spanEndSubst;
                context = new LineContext(
                    checkRule.delegate,
                    context);
                keywords = context.rules.getKeywords();

                break;
            //}}}
            //{{{ MARK_FOLLOWING
            case ParserRule.MARK_FOLLOWING:
                tokenHandler.handleToken(line,(checkRule.action
                    & ParserRule.EXCLUDE_MATCH)
                    == ParserRule.EXCLUDE_MATCH ?
                    context.rules.getDefault()
                    : checkRule.token,pos - line.offset,
                    pattern.count,context);

                context.spanEndSubst = null;
                context.inRule = checkRule;
                break;
            //}}}
            //{{{ MARK_PREVIOUS
            case ParserRule.MARK_PREVIOUS:
                context.spanEndSubst = null;

                if ((checkRule.action & ParserRule.EXCLUDE_MATCH)
                    == ParserRule.EXCLUDE_MATCH)
                {
                    if(pos != lastOffset)
                    {
                        tokenHandler.handleToken(line,
                            checkRule.token,
                            lastOffset - line.offset,
                            pos - lastOffset,
                            context);
                    }

                    tokenHandler.handleToken(line,
                        context.rules.getDefault(),
                        pos - line.offset,pattern.count,
                        context);
                }
                else
                {
                    tokenHandler.handleToken(line,
                        checkRule.token,
                        lastOffset - line.offset,
                        pos - lastOffset + pattern.count,
                        context);
                }

                break;
            //}}}
            default:
                throw new InternalError("Unhandled major action");
            }

            // move pos to last character of match sequence
            pos += (matchedChars - 1);
            lastOffset = pos + 1;

            // break out of inner for loop to check next char
        } //}}}
        //{{{ Handle end of MARK_FOLLOWING
        else if((context.inRule.action & ParserRule.MARK_FOLLOWING) != 0)
        {
            if(pos != lastOffset)
            {
                tokenHandler.handleToken(line,
                    context.inRule.token,
                    lastOffset - line.offset,
                    pos - lastOffset,context);
            }

            lastOffset = pos;
            context.inRule = null;
        } //}}}

        return true;
    } //}}}

///===///

//{{{ processKeyEvent() method
    public static KeyEvent processKeyEvent(KeyEvent evt)
    {
        int keyCode = evt.getKeyCode();
        char ch = evt.getKeyChar();

        switch(evt.getID())
        {
        //{{{ KEY_PRESSED...
        case KeyEvent.KEY_PRESSED:
            lastKeyTime = evt.getWhen();
            // get rid of keys we never need to handle
            switch(keyCode)
            {
            case KeyEvent.VK_DEAD_GRAVE:
            case KeyEvent.VK_DEAD_ACUTE:
            case KeyEvent.VK_DEAD_CIRCUMFLEX:
            case KeyEvent.VK_DEAD_TILDE:
            case KeyEvent.VK_DEAD_MACRON:
            case KeyEvent.VK_DEAD_BREVE:
            case KeyEvent.VK_DEAD_ABOVEDOT:
            case KeyEvent.VK_DEAD_DIAERESIS:
            case KeyEvent.VK_DEAD_ABOVERING:
            case KeyEvent.VK_DEAD_DOUBLEACUTE:
            case KeyEvent.VK_DEAD_CARON:
            case KeyEvent.VK_DEAD_CEDILLA:
            case KeyEvent.VK_DEAD_OGONEK:
            case KeyEvent.VK_DEAD_IOTA:
            case KeyEvent.VK_DEAD_VOICED_SOUND:
            case KeyEvent.VK_DEAD_SEMIVOICED_SOUND:
            case '\0':
                return null;
            case KeyEvent.VK_ALT:
                modifiers |= InputEvent.ALT_MASK;
                return null;
            case KeyEvent.VK_ALT_GRAPH:
                modifiers |= InputEvent.ALT_GRAPH_MASK;
                return null;
            case KeyEvent.VK_CONTROL:
                modifiers |= InputEvent.CTRL_MASK;
                return null;
            case KeyEvent.VK_SHIFT:
                modifiers |= InputEvent.SHIFT_MASK;
                return null;
            case KeyEvent.VK_META:
                modifiers |= InputEvent.META_MASK;
                return null;
            default:
                if(!evt.isMetaDown())
                {
                    if(evt.isControlDown()
                        && evt.isAltDown())
                    {
                        lastKeyTime = 0L;
                    }
                    else if(!evt.isControlDown()
                        && !evt.isAltDown())
                    {
                        lastKeyTime = 0L;

                        if(keyCode >= KeyEvent.VK_0
                            && keyCode <= KeyEvent.VK_9)
                        {
                            return null;
                        }

                        if(keyCode >= KeyEvent.VK_A
                            && keyCode <= KeyEvent.VK_Z)
                        {
                            return null;
                        }
                    }
                }

                if(Debug.ALT_KEY_PRESSED_DISABLED)
                {
                    /* we don't handle key pressed A+ */
                    /* they're too troublesome */
                    if((modifiers & InputEvent.ALT_MASK) != 0)
                        return null;
                }

                switch(keyCode)
                {
                case KeyEvent.VK_NUMPAD0:
                case KeyEvent.VK_NUMPAD1:
                case KeyEvent.VK_NUMPAD2:
                case KeyEvent.VK_NUMPAD3:
                case KeyEvent.VK_NUMPAD4:
                case KeyEvent.VK_NUMPAD5:
                case KeyEvent.VK_NUMPAD6:
                case KeyEvent.VK_NUMPAD7:
                case KeyEvent.VK_NUMPAD8:
                case KeyEvent.VK_NUMPAD9:
                case KeyEvent.VK_MULTIPLY:
                case KeyEvent.VK_ADD:
                /* case KeyEvent.VK_SEPARATOR: */
                case KeyEvent.VK_SUBTRACT:
                case KeyEvent.VK_DECIMAL:
                case KeyEvent.VK_DIVIDE:
                    last = LAST_NUMKEYPAD;
                    break;
                default:
                    last = LAST_NOTHING;
                    break;
                }

                return evt;
            }
        //}}}
        //{{{ KEY_TYPED...
        case KeyEvent.KEY_TYPED:
            // need to let \b through so that backspace will work
            // in HistoryTextFields
            if((ch < 0x20 || ch == 0x7f || ch == 0xff)
                && ch != '\b' && ch != '\t' && ch != '\n')
            {
                return null;
            }

            if(evt.getWhen() - lastKeyTime < 750)
            {
                if(!Debug.ALTERNATIVE_DISPATCHER)
                {
                    if(((modifiers & InputEvent.CTRL_MASK) != 0
                        ^ (modifiers & InputEvent.ALT_MASK) != 0)
                        || (modifiers & InputEvent.META_MASK) != 0)
                    {
                        return null;
                    }
                }

                // if the last key was a numeric keypad key
                // and NumLock is off, filter it out
                if(last == LAST_NUMKEYPAD)
                {
                    last = LAST_NOTHING;
                    if((ch >= '0' && ch <= '9') || ch == '.'
                        || ch == '/' || ch == '*'
                        || ch == '-' || ch == '+')
                    {
                        return null;
                    }
                }
                // Windows JDK workaround
                else if(last == LAST_ALT)
                {
                    last = LAST_NOTHING;
                    switch(ch)
                    {
                    case 'B':
                    case 'M':
                    case 'X':
                    case 'c':
                    case '!':
                    case ',':
                    case '?':
                        return null;
                    }
                }
            }
            else
            {
                if((modifiers & InputEvent.SHIFT_MASK) != 0)
                {
                    switch(ch)
                    {
                    case '\n':
                    case '\t':
                        return null;
                    }
                }
                modifiers = 0;
            }

            return evt;
        //}}}
        //{{{ KEY_RELEASED...
        case KeyEvent.KEY_RELEASED:
            switch(keyCode)
            {
            case KeyEvent.VK_ALT:
                modifiers &= ~InputEvent.ALT_MASK;
                lastKeyTime = evt.getWhen();
                // we consume this to work around the bug
                // where A+TAB window switching activates
                // the menu bar on Windows.
                evt.consume();
                return null;
            case KeyEvent.VK_ALT_GRAPH:
                modifiers &= ~InputEvent.ALT_GRAPH_MASK;
                return null;
            case KeyEvent.VK_CONTROL:
                modifiers &= ~InputEvent.CTRL_MASK;
                return null;
            case KeyEvent.VK_SHIFT:
                modifiers &= ~InputEvent.SHIFT_MASK;
                return null;
            case KeyEvent.VK_META:
                modifiers &= ~InputEvent.META_MASK;
                return null;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_PAGE_UP:
            case KeyEvent.VK_PAGE_DOWN:
            case KeyEvent.VK_END:
            case KeyEvent.VK_HOME:
                /* workaround for A+keys producing
                 * garbage on Windows */
                if(modifiers == InputEvent.ALT_MASK)
                    last = LAST_ALT;
                break;
            }
            return evt;
        //}}}
        default:
            return evt;
        }
    } //}}}

/// COLUMBA

public void launchWizard(final String pluginID, boolean firstTime) {
        final AbstractExternalToolsPlugin plugin;
        IExtensionHandler handler = null;

        try {
            handler = PluginManager.getInstance()
                    .getExtensionHandler(IExtensionHandlerKeys.ORG_COLUMBA_CORE_EXTERNALTOOLS);
        } catch (PluginHandlerNotFoundException e) {
            e.printStackTrace();
        }

        try {
            IExtension extension = handler.getExtension(pluginID);

            plugin = (AbstractExternalToolsPlugin) extension
                    .instanciateExtension(null);
        } catch (Exception e1) {
            e1.printStackTrace();

            return;
        }

        data = new DataModel();
        data.registerDataLookup("id", new DataLookup() {
            public Object lookupData() {
                return pluginID;
            }
        });

        data.registerDataLookup("Plugin", new DataLookup() {
            public Object lookupData() {
                return plugin;
            }
        });

        WizardModel model;

        if (firstTime) {
            model = new DefaultWizardModel(new Step[] {
                    new DescriptionStep(data), new LocationStep(data) });
        } else {
            model = new DefaultWizardModel(new Step[] { new InfoStep(),
                    new DescriptionStep(data), new LocationStep(data) });
        }

        listener = new ExternalToolsWizardModelListener(data);
        model.addWizardModelListener(listener);

        // TODO (@author fdietz): i18n
        Wizard wizard = new Wizard(model, "External Tools Configuration",
                ImageLoader.getSmallIcon(IconKeys.PREFERENCES));
        wizard.setStepListRenderer(null);
        CSH.setHelpIDString(wizard, "extending_columba_2");
        JavaHelpSupport.enableHelp(wizard, HelpManager.getInstance()
                .getHelpBroker());

        wizard.pack();
        wizard.setLocationRelativeTo(null);
        wizard.setVisible(true);
    }


    /**
     * Starts the server.
     * 
     * @throws IOException
     */
    public synchronized void start() throws IOException {
        if (!isRunning()) {
            int port;
            int count = 0;

            while (serverSocket == null) {
                // create random port number within range
                port = random.nextInt(65536 - LOWEST_PORT) + LOWEST_PORT;

                try {
                    serverSocket = new ServerSocket(port);

                    // store port number in file
                    SessionController.serializePortNumber(port);
                } catch (SocketException se) { // port is in use, try next
                    count++;

                    if (count == 10) { // something is very wrong here
                        JOptionPane.showMessageDialog(null,
                                GlobalResourceLoader.getString(RESOURCE_PATH,
                                        "session", "err_10se_msg"),
                                GlobalResourceLoader.getString(RESOURCE_PATH,
                                        "session", "err_10se_title"),
                                JOptionPane.ERROR_MESSAGE);

                        // this is save because the only shutdown plugin
                        // to stop this server, the configuration isn't touched
                        System.exit(1);
                    }
                }
            }

            serverSocket.setSoTimeout(2000);
            thread.start();
        }
    }



   /**
     * Import all mailbox files in Columba. This method makes use of the
     * importMailbox method you have to override and simply iterates over all
     * given files/directories.
     *
     * @param worker
     */
    public void importMailbox(IWorkerStatusController worker) {
        File[] listing = getSourceFiles();

        for (int i = 0; i < listing.length; i++) {
            if (worker.cancelled()) {
                return;
            }

            try {
                importMailboxFile(listing[i], worker, getDestinationFolder());
            } catch (Exception ex) {
                //TODO (@author fdietz): i18n
                int result = JOptionPane.showConfirmDialog(
                        FrameManager.getInstance().getActiveFrame(),
                    "An error occured while importing a message. Try again?",
                    "Retry message import?",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    i--;
                } else if (result == JOptionPane.CANCEL_OPTION) {
                    worker.cancel();
                }
            }
        }
    }


/**
     * This method calls your overridden importMailbox(File)-method
     * and handles exceptions.
     */
    public void run(IWorkerStatusController worker) {
        //TODO (@author fdietz): i18n
        worker.setDisplayText("Importing messages...");

        importMailbox(worker);

        if (getCount() == 0) {
            //TODO (@author fdietz): i18n
            JOptionPane.showMessageDialog(FrameManager.getInstance()
                    .getActiveFrame(),
                "Message import failed! No messages were added to the folder.\n" +
                "This means that the parser didn't throw any exception even if " +
                "it didn't recognize the mailbox format or the messagebox simply " +
                "didn't contain any messages.",
                "Warning", JOptionPane.WARNING_MESSAGE);

            return;
        } else {
            //TODO (@author fdietz): i18n
            JOptionPane.showMessageDialog(null,
                "Message import was successful!", "Information",
                JOptionPane.INFORMATION_MESSAGE,
                ImageLoader.getIcon(IconKeys.DIALOG_INFO));
        }
    }


    public void stateChanged(ChangeEvent e) {
        if (ConnectionStateImpl.getInstance().isOnline()) {
            onlineButton.setIcon(onlineIcon);
            // TODO (@author fdietz): i18n
            onlineButton.setToolTipText("You are in ONLINE state");
        } else {
            onlineButton.setIcon(offlineIcon);
            // TODO (@author fdietz): i18n
            onlineButton.setToolTipText("You are in OFFLINE state");
        }
    }


/**
     * @see org.columba.mail.spam.ISpamPlugin#save()
     */
    public void save() {
        try {
            // only save if changes exist
            if (alreadyLoaded && hasChanged) {
                // cleanup DB -> remove old tokens
                db.cleanupDB(THRESHOLD);

                // close DB
                db.close();
            }
        } catch (Exception e) {
            if (Logging.DEBUG) {
                e.printStackTrace();
            }
            // TODO (@author fdietz): i18n
            int value = JOptionPane.showConfirmDialog(FrameManager.getInstance()
                    .getActiveFrame(),
                    "An error occured while saving the spam database.\n"
                            + "Try again?", "Error saving database",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (value == JOptionPane.YES_OPTION) {
                save();
            }
        }

    }


private void initComponents() {
        imageLabel = new JLabel(UIManager.getIcon("OptionPane.errorIcon"),
                SwingConstants.LEFT);

        messageMultiLineLabel = new MultiLineLabel(message);

        messageMultiLineLabel.setFont(messageMultiLineLabel.getFont()
                .deriveFont(Font.BOLD));
        label = new JLabel(message);
        label.setFont(label.getFont().deriveFont(Font.BOLD));

        stacktraceTextArea = new JTextArea();

        if ( ex != null ) {
            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));
    
            stacktraceTextArea.append(stringWriter.toString());
            stacktraceTextArea.setEditable(false);
        }
        
        // TODO (@author fdietz): i18n
        detailsButton = new JToggleButton("Details >>");
        detailsButton.setSelected(false);
        detailsButton.setActionCommand("DETAILS");
        detailsButton.addActionListener(this);
        
        if ( ex == null ) {
            detailsButton.setEnabled(false);
        }
        
        closeButton = new ButtonWithMnemonic(GlobalResourceLoader.getString(
                "global", "global", "close"));
        closeButton.setActionCommand(CMD_CLOSE);
        closeButton.addActionListener(this);

        reportBugButton = new ButtonWithMnemonic(GlobalResourceLoader
                .getString(RESOURCE_BUNDLE_PATH, "exception", "report_bug"));
        reportBugButton.setActionCommand(CMD_REPORT_BUG);
        reportBugButton.addActionListener(this);
    }


/**
     * @see org.columba.api.command.Command#execute(org.columba.api.command.Worker)
     */
    public void execute(IWorkerStatusController worker) throws Exception {

        // get array of source references
        IMailFolderCommandReference r = (IMailFolderCommandReference) getReference();

        // get array of message UIDs
        Object[] uids = r.getUids();

        // get source folder
        IMailbox srcFolder = (IMailbox) r.getSourceFolder();

        // register for status events
        ((StatusObservableImpl) srcFolder.getObservable()).setWorker(worker);

        // update status message
        if (uids.length > 1) {
            // TODO (@author fdietz): i18n
            worker.setDisplayText("Training messages...");
            worker.setProgressBarMaximum(uids.length);
        }

        long startTime = System.currentTimeMillis();

        for (int j = 0; j < uids.length; j++) {
            if (worker.cancelled()) {
                break;
            }

            try {

                // train message as ham
                SpamController.getInstance().trainMessageAsHam(srcFolder,
                        uids[j]);

                if (uids.length > 1) {
                    worker.setProgressBarValue(j);
                }
            } catch (Exception e) {
                if (Logging.DEBUG) {
                    e.printStackTrace();
                }
            }
        }

        long endTime = System.currentTimeMillis();

        LOG.info("took me=" + (endTime - startTime) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$

    }
}    


/**
     * @see org.columba.api.command.Command#execute(org.columba.api.command.Worker)
     */
    public void execute(IWorkerStatusController worker) throws Exception {

        // get array of source references
        IMailFolderCommandReference r = (IMailFolderCommandReference) getReference();

        // get array of message UIDs
        Object[] uids = r.getUids();

        // get source folder
        IMailbox srcFolder = (IMailbox) r.getSourceFolder();

        // register for status events
        ((StatusObservableImpl) srcFolder.getObservable()).setWorker(worker);

        // update status message
        if (uids.length > 1) {
            // TODO (@author fdietz): i18n
            worker.setDisplayText("Training messages...");
            worker.setProgressBarMaximum(uids.length);
        }

        for (int j = 0; j < uids.length; j++) {
            if (worker.cancelled()) {
                break;
            }

            try {

                // train message as spam
                SpamController.getInstance().trainMessageAsSpam(srcFolder,
                        uids[j]);

                if (uids.length > 1) {
                    worker.setProgressBarValue(j);
                }
            } catch (Exception e) {
                if (Logging.DEBUG) {
                    e.printStackTrace();
                }
            }
        }

    }


public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        handleClient(serverSocket.accept());
                    } catch (SocketTimeoutException ste) {
                        // do nothing here, just continue
                    } catch (IOException ioe) {
                        ioe.printStackTrace();

                        // what to do here? we could start a new server...
                    }
                }

                try {
                    serverSocket.close();

                    // cleanup: remove port number file
                    SessionController.serializePortNumber(-1);
                } catch (IOException ioe) {
                }

                serverSocket = null;
            }


protected void activateLocalClusterNode() {
       
        // Regions can get instantiated in the course of normal work (e.g.
        // a named query region will be created the first time the query is
        // executed), so suspend any ongoing tx
        Transaction tx = suspend();
        try {
            Configuration cfg = jbcCache.getConfiguration();
            if (cfg.isUseRegionBasedMarshalling()) {
                org.jboss.cache.Region jbcRegion = jbcCache.getRegion(regionFqn, true);
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = getClass().getClassLoader();
                }
                jbcRegion.registerContextClassLoader(classLoader);
                if ( !jbcRegion.isActive() ) {
                    jbcRegion.activate();
                }
            }
            
//            // If we are using replication, we may remove the root node
//            // and then need to re-add it. In that case, the fact
//            // that it is resident will not replicate, so use a listener
//            // to set it as resident
//            if (CacheHelper.isClusteredReplication(cfg.getCacheMode()) 
//                  || CacheHelper.isClusteredInvalidation(cfg.getCacheMode())) {
//                listener = new RegionRootListener();
//                jbcCache.addCacheListener(listener);
//            }
            
            regionRoot = jbcCache.getRoot().getChild( regionFqn );
            if (regionRoot == null || !regionRoot.isValid()) {
               // Establish the region root node with a non-locking data version
               DataVersion version = optimistic ? NonLockingDataVersion.INSTANCE : null;
               regionRoot = CacheHelper.addNode(jbcCache, regionFqn, true, true, version);
            }
            else if (optimistic && regionRoot instanceof NodeSPI) {
                // FIXME Hacky workaround to JBCACHE-1202
                if ( !( ( ( NodeSPI ) regionRoot ).getVersion() instanceof NonLockingDataVersion ) ) {
                    ((NodeSPI) regionRoot).setVersion(NonLockingDataVersion.INSTANCE);
                }
            }
            if (!regionRoot.isResident()) {
               regionRoot.setResident(true);
            }
        }
        catch (Exception e) {
            throw new CacheException(e.getMessage(), e);
        }
        finally {
            if (tx != null)
               resume(tx);
        }
        
    }


   // TODO: copy/paste from ManyToOneType

    public Serializable disassemble(Object value, SessionImplementor session, Object owner)
    throws HibernateException {

        if ( isNotEmbedded(session) ) {
            return getIdentifierType(session).disassemble(value, session, owner);
        }
        
        if (value==null) {
            return null;
        }
        else {
            // cache the actual id of the object, not the value of the
            // property-ref, which might not be initialized
            Object id = ForeignKeys.getEntityIdentifierIfNotUnsaved( getAssociatedEntityName(), value, session );
            if (id==null) {
                throw new AssertionFailure(
                        "cannot cache a reference to an object with a null id: " + 
                        getAssociatedEntityName() 
                );
            }
            return getIdentifierType(session).disassemble(id, session, owner);
        }
    }


// This method may be called many times!!
    protected void secondPassCompile() throws MappingException {
        log.debug( "processing extends queue" );

        processExtendsQueue();

        log.debug( "processing collection mappings" );

        Iterator iter = secondPasses.iterator();
        while ( iter.hasNext() ) {
            SecondPass sp = (SecondPass) iter.next();
            if ( ! (sp instanceof QuerySecondPass) ) {
                sp.doSecondPass( classes );
                iter.remove();
            }
        }

        log.debug( "processing native query and ResultSetMapping mappings" );
        iter = secondPasses.iterator();
        while ( iter.hasNext() ) {
            SecondPass sp = (SecondPass) iter.next();
            sp.doSecondPass( classes );
            iter.remove();
        }

        log.debug( "processing association property references" );

        iter = propertyReferences.iterator();
        while ( iter.hasNext() ) {
            Mappings.PropertyReference upr = (Mappings.PropertyReference) iter.next();

            PersistentClass clazz = getClassMapping( upr.referencedClass );
            if ( clazz == null ) {
                throw new MappingException(
                        "property-ref to unmapped class: " +
                        upr.referencedClass
                    );
            }

            Property prop = clazz.getReferencedProperty( upr.propertyName );
            if ( upr.unique ) {
                ( (SimpleValue) prop.getValue() ).setAlternateUniqueKey( true );
            }
        }

        //TODO: Somehow add the newly created foreign keys to the internal collection

        log.debug( "processing foreign key constraints" );

        iter = getTableMappings();
        Set done = new HashSet();
        while ( iter.hasNext() ) {
            secondPassCompileForeignKeys( (Table) iter.next(), done );
        }

    }


void createPrimaryKey() {
        if ( !isOneToMany() ) {
            PrimaryKey pk = new PrimaryKey();
            pk.addColumns( getKey().getColumnIterator() );
            Iterator iter = getElement().getColumnIterator();
            while ( iter.hasNext() ) {
                Object selectable = iter.next();
                if ( selectable instanceof Column ) {
                    Column col = (Column) selectable;
                    if ( !col.isNullable() ) {
                        pk.addColumn(col);
                    }
                }
            }
            if ( pk.getColumnSpan()==getKey().getColumnSpan() ) { 
                //for backward compatibility, allow a set with no not-null 
                //element columns, using all columns in the row locater SQL
                //TODO: create an implicit not null constraint on all cols?
            }
            else {
                getCollectionTable().setPrimaryKey(pk);
            }
        }
        else {
            //create an index on the key columns??
        }
    }


public String toSqlString(Criteria criteria, CriteriaQuery criteriaQuery) 
    throws HibernateException {
        String[] xcols = criteriaQuery.getColumnsUsingProjection(criteria, propertyName);
        String[] ycols = criteriaQuery.getColumnsUsingProjection(criteria, otherPropertyName);
        String result = StringHelper.join(
            " and ",
            StringHelper.add(xcols, getOp(), ycols)
        );
        if (xcols.length>1) result = '(' + result + ')';
        return result;
        //TODO: get SQL rendering out of this package!
    }


public static void bindManyToOne(Element node, ManyToOne manyToOne, String path,
            boolean isNullable, Mappings mappings) throws MappingException {

        bindColumnsOrFormula( node, manyToOne, path, isNullable, mappings );
        initOuterJoinFetchSetting( node, manyToOne );
        initLaziness( node, manyToOne, mappings, true );

        Attribute ukName = node.attribute( "property-ref" );
        if ( ukName != null ) {
            manyToOne.setReferencedPropertyName( ukName.getValue() );
        }

        manyToOne.setReferencedEntityName( getEntityName( node, mappings ) );

        String embed = node.attributeValue( "embed-xml" );
        manyToOne.setEmbedded( embed == null || "true".equals( embed ) );

        String notFound = node.attributeValue( "not-found" );
        manyToOne.setIgnoreNotFound( "ignore".equals( notFound ) );

        if( ukName != null && !manyToOne.isIgnoreNotFound() ) {
            if ( !node.getName().equals("many-to-many") ) { //TODO: really bad, evil hack to fix!!!
                mappings.addSecondPass( new ManyToOneSecondPass(manyToOne) );
            }
        }

        Attribute fkNode = node.attribute( "foreign-key" );
        if ( fkNode != null ) manyToOne.setForeignKeyName( fkNode.getValue() );

        validateCascade( node, path );
    }


    public String toSqlString(Criteria criteria, CriteriaQuery criteriaQuery)
    throws HibernateException {
        Dialect dialect = criteriaQuery.getFactory().getDialect();
        String[] columns = criteriaQuery.getColumnsUsingProjection(criteria, propertyName);
        if (columns.length!=1) throw new HibernateException("ilike may only be used with single-column properties");
        if ( dialect instanceof PostgreSQLDialect ) {
            return columns[0] + " ilike ?";
        }
        else {
            return dialect.getLowercaseFunction() + '(' + columns[0] + ") like ?";
        }

        //TODO: get SQL rendering out of this package!
    }


**
     * Handle the given replicate event.
     *
     * @param event The replicate event to be handled.
     *
     * @throws TransientObjectException An invalid attempt to replicate a transient entity.
     */
    public void onReplicate(ReplicateEvent event) {
        final EventSource source = event.getSession();
        if ( source.getPersistenceContext().reassociateIfUninitializedProxy( event.getObject() ) ) {
            log.trace( "uninitialized proxy passed to replicate()" );
            return;
        }

        Object entity = source.getPersistenceContext().unproxyAndReassociate( event.getObject() );

        if ( source.getPersistenceContext().isEntryFor( entity ) ) {
            log.trace( "ignoring persistent instance passed to replicate()" );
            //hum ... should we cascade anyway? throw an exception? fine like it is?
            return;
        }

        EntityPersister persister = source.getEntityPersister( event.getEntityName(), entity );

        // get the id from the object
        /*if ( persister.isUnsaved(entity, source) ) {
            throw new TransientObjectException("transient instance passed to replicate()");
        }*/
        Serializable id = persister.getIdentifier( entity, source.getEntityMode() );
        if ( id == null ) {
            throw new TransientObjectException( "instance with null id passed to replicate()" );
        }

        final ReplicationMode replicationMode = event.getReplicationMode();

        final Object oldVersion;
        if ( replicationMode == ReplicationMode.EXCEPTION ) {
            //always do an INSERT, and let it fail by constraint violation
            oldVersion = null;
        }
        else {
            //what is the version on the database?
            oldVersion = persister.getCurrentVersion( id, source );         
        }

        if ( oldVersion != null ) {             
            if ( log.isTraceEnabled() ) {
                log.trace(
                        "found existing row for " +
                                MessageHelper.infoString( persister, id, source.getFactory() )
                );
            }

            /// HHH-2378
            final Object realOldVersion = persister.isVersioned() ? oldVersion : null;
            
            boolean canReplicate = replicationMode.shouldOverwriteCurrentVersion(
                    entity,
                    realOldVersion,
                    persister.getVersion( entity, source.getEntityMode() ),
                    persister.getVersionType()
            );

            if ( canReplicate ) {
                //will result in a SQL UPDATE:
                performReplication( entity, id, realOldVersion, persister, replicationMode, source );
            }
            else {
                //else do nothing (don't even reassociate object!)
                log.trace( "no need to replicate" );
            }

            //TODO: would it be better to do a refresh from db?
        }
        else {
            // no existing row - do an insert
            if ( log.isTraceEnabled() ) {
                log.trace(
                        "no existing row, replicating new instance " +
                                MessageHelper.infoString( persister, id, source.getFactory() )
                );
            }

            final boolean regenerate = persister.isIdentifierAssignedByInsert(); // prefer re-generation of identity!
            final EntityKey key = regenerate ?
                    null : new EntityKey( id, persister, source.getEntityMode() );

            performSaveOrReplicate(
                    entity,
                    key,
                    persister,
                    regenerate,
                    replicationMode,
                    source,
                    true
            );

        }
    }


    /**
     * Returns either the table name if explicit or
     * if there is an associated table, the concatenation of owner entity table and associated table
     * otherwise the concatenation of owner entity table and the unqualified property name
     */
    public String logicalCollectionTableName(String tableName,
                                             String ownerEntityTable, String associatedEntityTable, String propertyName
    ) {
        if ( tableName != null ) {
            return tableName;
        }
        else {
            //use of a stringbuffer to workaround a JDK bug
            return new StringBuffer(ownerEntityTable).append("_")
                    .append(
                        associatedEntityTable != null ?
                        associatedEntityTable :
                        StringHelper.unqualify( propertyName )
                    ).toString();
        }
    }


public Object invoke(
                Object object,
                Method method,
                Method method1,
                Object[] args) throws Exception {
            String name = method.getName();
            if ( "toString".equals( name ) ) {
                return proxiedClassName + "@" + System.identityHashCode( object );
            }
            else if ( "equals".equals( name ) ) {
                return proxiedObject == object ? Boolean.TRUE : Boolean.FALSE;
            }
            else if ( "hashCode".equals( name ) ) {
                return new Integer( System.identityHashCode( object ) );
            }
            boolean hasGetterSignature = method.getParameterTypes().length == 0 && method.getReturnType() != null;
            boolean hasSetterSignature = method.getParameterTypes().length == 1 && ( method.getReturnType() == null || method.getReturnType() == void.class );
            if ( name.startsWith( "get" ) && hasGetterSignature ) {
                String propName = name.substring( 3 );
                return data.get( propName );
            }
            else if ( name.startsWith( "is" ) && hasGetterSignature ) {
                String propName = name.substring( 2 );
                return data.get( propName );
            }
            else if ( name.startsWith( "set" ) && hasSetterSignature) {
                String propName = name.substring( 3 );
                data.put( propName, args[0] );
                return null;
            }
            else {
                // todo : what else to do here?
                return null;
            }
        }





