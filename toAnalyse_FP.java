/**
     * Helper method to check to see if a weblogic EBJ1.1 jar needs to be
     * rebuilt using ejbc. Called from writeJar it sees if the "Bean" classes
     * are the only thing that needs to be updated and either updates the Jar
     * with the Bean classfile or returns true, saying that the whole weblogic
     * jar needs to be regened with ejbc. This allows faster build times for
     * working developers. <p>
     *
     * The way weblogic ejbc works is it creates wrappers for the publicly
     * defined methods as they are exposed in the remote interface. If the
     * actual bean changes without changing the the method signatures then
     * only the bean classfile needs to be updated and the rest of the
     * weblogic jar file can remain the same. If the Interfaces, ie. the
     * method signatures change or if the xml deployment descriptors changed,
     * the whole jar needs to be rebuilt with ejbc. This is not strictly true
     * for the xml files. If the JNDI name changes then the jar doesnt have to
     * be rebuild, but if the resources references change then it does. At
     * this point the weblogic jar gets rebuilt if the xml files change at
     * all.
     *
     * @param genericJarFile java.io.File The generic jar file.
     * @param weblogicJarFile java.io.File The weblogic jar file to check to
     *      see if it needs to be rebuilt.
     * @return true if the jar needs to be rebuilt.
     */
    protected boolean isRebuildRequired(File genericJarFile, File weblogicJarFile) {
        boolean rebuild = false;

        JarFile genericJar = null;
        JarFile wlJar = null;
        File newWLJarFile = null;
        JarOutputStream newJarStream = null;
        ClassLoader genericLoader = null;

        try {
            log("Checking if weblogic Jar needs to be rebuilt for jar " + weblogicJarFile.getName(),
                Project.MSG_VERBOSE);
            // Only go forward if the generic and the weblogic file both exist
            if (genericJarFile.exists() && genericJarFile.isFile()
                 && weblogicJarFile.exists() && weblogicJarFile.isFile()) {
                //open jar files
                genericJar = new JarFile(genericJarFile);
                wlJar = new JarFile(weblogicJarFile);

                Hashtable genericEntries = new Hashtable();
                Hashtable wlEntries = new Hashtable();
                Hashtable replaceEntries = new Hashtable();

                //get the list of generic jar entries
                for (Enumeration e = genericJar.entries(); e.hasMoreElements();) {
                    JarEntry je = (JarEntry) e.nextElement();

                    genericEntries.put(je.getName().replace('\\', '/'), je);
                }
                //get the list of weblogic jar entries
                for (Enumeration e = wlJar.entries(); e.hasMoreElements();) {
                    JarEntry je = (JarEntry) e.nextElement();

                    wlEntries.put(je.getName(), je);
                }

                //Cycle Through generic and make sure its in weblogic
                genericLoader = getClassLoaderFromJar(genericJarFile);

                for (Enumeration e = genericEntries.keys(); e.hasMoreElements();) {
                    String filepath = (String) e.nextElement();

                    if (wlEntries.containsKey(filepath)) {
                        // File name/path match

                        // Check files see if same
                        JarEntry genericEntry = (JarEntry) genericEntries.get(filepath);
                        JarEntry wlEntry = (JarEntry) wlEntries.get(filepath);

                        if ((genericEntry.getCrc() != wlEntry.getCrc())
                            || (genericEntry.getSize() != wlEntry.getSize())) {

                            if (genericEntry.getName().endsWith(".class")) {
                                //File are different see if its an object or an interface
                                String classname
                                    = genericEntry.getName().replace(File.separatorChar, '.');

                                classname = classname.substring(0, classname.lastIndexOf(".class"));

                                Class genclass = genericLoader.loadClass(classname);

                                if (genclass.isInterface()) {
                                    //Interface changed   rebuild jar.
                                    log("Interface " + genclass.getName()
                                        + " has changed", Project.MSG_VERBOSE);
                                    rebuild = true;
                                    break;
                                } else {
                                    //Object class Changed   update it.
                                    replaceEntries.put(filepath, genericEntry);
                                }
                            } else {
                                // is it the manifest. If so ignore it
                                if (!genericEntry.getName().equals("META-INF/MANIFEST.MF")) {
                                    //File other then class changed   rebuild
                                    log("Non class file " + genericEntry.getName()
                                        + " has changed", Project.MSG_VERBOSE);
                                    rebuild = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        // a file doesnt exist rebuild

                        log("File " + filepath + " not present in weblogic jar",
                            Project.MSG_VERBOSE);
                        rebuild = true;
                        break;
                    }
                }

                if (!rebuild) {
                    log("No rebuild needed - updating jar", Project.MSG_VERBOSE);
                    newWLJarFile = new File(weblogicJarFile.getAbsolutePath() + ".temp");
                    if (newWLJarFile.exists()) {
                        newWLJarFile.delete();
                    }

                    newJarStream = new JarOutputStream(new FileOutputStream(newWLJarFile));
                    newJarStream.setLevel(0);

                    //Copy files from old weblogic jar
                    for (Enumeration e = wlEntries.elements(); e.hasMoreElements();) {
                        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                        int bytesRead;
                        InputStream is;
                        JarEntry je = (JarEntry) e.nextElement();

                        if (je.getCompressedSize() == -1
                            || je.getCompressedSize() == je.getSize()) {
                            newJarStream.setLevel(0);
                        } else {
                            newJarStream.setLevel(JAR_COMPRESS_LEVEL);
                        }

                        // Update with changed Bean class
                        if (replaceEntries.containsKey(je.getName())) {
                            log("Updating Bean class from generic Jar "
                                + je.getName(), Project.MSG_VERBOSE);
                            // Use the entry from the generic jar
                            je = (JarEntry) replaceEntries.get(je.getName());
                            is = genericJar.getInputStream(je);
                        } else {
                            //use fle from original weblogic jar

                            is = wlJar.getInputStream(je);
                        }
                        newJarStream.putNextEntry(new JarEntry(je.getName()));

                        while ((bytesRead = is.read(buffer)) != -1) {
                            newJarStream.write(buffer, 0, bytesRead);
                        }
                        is.close();
                    }
                } else {
                    log("Weblogic Jar rebuild needed due to changed "
                         + "interface or XML", Project.MSG_VERBOSE);
                }
            } else {
                rebuild = true;
            }
        } catch (ClassNotFoundException cnfe) {
            String cnfmsg = "ClassNotFoundException while processing ejb-jar file"
                 + ". Details: "
                 + cnfe.getMessage();

            throw new BuildException(cnfmsg, cnfe);
        } catch (IOException ioe) {
            String msg = "IOException while processing ejb-jar file "
                 + ". Details: "
                 + ioe.getMessage();

            throw new BuildException(msg, ioe);
        } finally {
            // need to close files and perhaps rename output
            if (genericJar != null) {
                try {
                    genericJar.close();
                } catch (IOException closeException) {
                    // empty
                }
            }

            if (wlJar != null) {
                try {
                    wlJar.close();
                } catch (IOException closeException) {
                    // empty
                }
            }

            if (newJarStream != null) {
                try {
                    newJarStream.close();
                } catch (IOException closeException) {
                    // empty
                }

                try {
                    FILE_UTILS.rename(newWLJarFile, weblogicJarFile);
                } catch (IOException renameException) {
                    log(renameException.getMessage(), Project.MSG_WARN);
                    rebuild = true;
                }
            }
            if (genericLoader != null
                && genericLoader instanceof AntClassLoader) {
                AntClassLoader loader = (AntClassLoader) genericLoader;
                loader.cleanup();
            }
        }

        return rebuild;
    }

///===///

/**
     * Processes (checks out) <code>stFiles</code>files from StarTeam folder.
     *
     * @param eachFile repository file to process
     * @param targetFolder a java.io.File (Folder) to work
     * @throws IOException when StarTeam API fails to work with files
     */
    private void processFile(com.starbase.starteam.File eachFile,
                             File targetFolder)
    throws IOException {
        String filename = eachFile.getName();

        java.io.File localFile = new java.io.File(targetFolder, filename);

        // If the file doesn't pass the include/exclude tests, skip it.
        if (!shouldProcess(filename)) {
            log("Excluding " + getFullRepositoryPath(eachFile),
                Project.MSG_INFO);
                return;
        }

        if (this.isUsingRevisionLabel()) {
            if (!targetFolder.exists()) {
                if (targetFolder.mkdirs()) {
                    log("Creating folder: " + targetFolder);
                } else {
                    throw new BuildException(
                        "Failed to create local folder " + targetFolder);
                }
            }
            boolean success = eachFile.checkoutByLabelID(
                localFile,
                getIDofLabelInUse(),
                this.lockStatus,
                !this.useRepositoryTimeStamp,
                true,
                false);
            if (success) {
                log("Checked out " + describeCheckout(eachFile, localFile));
            }
        } else {
            boolean checkout = true;

            // Just a note: StarTeam has a status for NEW which implies
            // that there is an item  on your local machine that is not
            // in the repository.  These are the items that show up as
            // NOT IN VIEW in the Starteam GUI.
            // One would think that we would want to perhaps checkin the
            // NEW items (not in all cases! - Steve Cohen 15 Dec 2001)
            // Unfortunately, the sdk doesn't really work, and we can't
            // actually see  anything with a status of NEW. That is why
            // we can just check out  everything here without worrying
            // about losing anything.

            int fileStatus = (eachFile.getStatus());

            // We try to update the status once to give StarTeam
            // another chance.

            if (fileStatus == Status.MERGE
                || fileStatus == Status.UNKNOWN) {
                eachFile.updateStatus(true, true);
                fileStatus = (eachFile.getStatus());
            }

            log(eachFile.toString() + " has status of "
                + Status.name(fileStatus), Project.MSG_DEBUG);


            switch (fileStatus) {
            case Status.OUTOFDATE:
            case Status.MISSING:
                log("Checking out: " + describeCheckout(eachFile));
                break;
            default:
                if (isForced() && fileStatus != Status.CURRENT) {
                    log("Forced checkout of "
                        + describeCheckout(eachFile)
                        + " over status " + Status.name(fileStatus));
                } else {
                    log("Skipping: " + getFullRepositoryPath(eachFile)
                        + " - status: " + Status.name(fileStatus));
                    checkout = false;
                }
            }

            if (checkout) {
                if (!targetFolder.exists()) {
                    if (targetFolder.mkdirs()) {
                        log("Creating folder: " + targetFolder);
                    } else {
                        throw new BuildException(
                            "Failed to create local folder " + targetFolder);
                    }
                }
                eachFile.checkout(this.lockStatus,
                                 !this.useRepositoryTimeStamp, this.convertEOL, false);
            }
        }
    }

///===///

/**
     * The method attempts to figure out where the executable is so that we can feed
     * the full path. We first try basedir, then the exec dir, and then
     * fallback to the straight executable name (i.e. on the path).
     *
     * @param exec the name of the executable.
     * @param mustSearchPath if true, the executable will be looked up in
     * the PATH environment and the absolute path is returned.
     *
     * @return the executable as a full path if it can be determined.
     *
     * @since Ant 1.6
     */
    protected String resolveExecutable(String exec, boolean mustSearchPath) {
        if (!resolveExecutable) {
            return exec;
        }
        // try to find the executable
        File executableFile = getProject().resolveFile(exec);
        if (executableFile.exists()) {
            return executableFile.getAbsolutePath();
        }
        // now try to resolve against the dir if given
        if (dir != null) {
            executableFile = FILE_UTILS.resolveFile(dir, exec);
            if (executableFile.exists()) {
                return executableFile.getAbsolutePath();
            }
        }
        // couldn't find it - must be on path
        if (mustSearchPath) {
            Path p = null;
            String[] environment = env.getVariables();
            if (environment != null) {
                for (int i = 0; i < environment.length; i++) {
                    if (isPath(environment[i])) {
                        p = new Path(getProject(), getPath(environment[i]));
                        break;
                    }
                }
            }
            if (p == null) {
                String path = getPath(Execute.getEnvironmentVariables());
                if (path != null) {
                    p = new Path(getProject(), path);
                }
            }
            if (p != null) {
                String[] dirs = p.list();
                for (int i = 0; i < dirs.length; i++) {
                    executableFile
                        = FILE_UTILS.resolveFile(new File(dirs[i]), exec);
                    if (executableFile.exists()) {
                        return executableFile.getAbsolutePath();
                    }
                }
            }
        }
        // mustSearchPath is false, or no PATH or not found - keep our
        // fingers crossed.
        return exec;
    }

///===///

    /**
     * Scans the directory looking for source files to be compiled and support
     * files to be copied.
     */
    private void scanDir(File srcDir, File destDir, String[] files) {
        for (int i = 0; i < files.length; i++) {
            File srcFile = new File(srcDir, files[i]);
            File destFile = new File(destDir, files[i]);
            String filename = files[i];
            // if it's a non source file, copy it if a later date than the
            // dest
            // if it's a source file, see if the destination class file
            // needs to be recreated via compilation
            if (filename.toLowerCase().endsWith(".nrx")) {
                File classFile =
                    new File(destDir,
                    filename.substring(0, filename.lastIndexOf('.')) + ".class");
                File javaFile =
                    new File(destDir,
                    filename.substring(0, filename.lastIndexOf('.'))
                    + (removeKeepExtension ? ".java" : ".java.keep"));

                // nocompile case tests against .java[.keep] file
                if (!compile && srcFile.lastModified() > javaFile.lastModified()) {
                    filecopyList.put(srcFile.getAbsolutePath(), destFile.getAbsolutePath());
                    compileList.addElement(destFile.getAbsolutePath());
                } else if (compile && srcFile.lastModified() > classFile.lastModified()) {
                    // compile case tests against .class file
                    filecopyList.put(srcFile.getAbsolutePath(), destFile.getAbsolutePath());
                    compileList.addElement(destFile.getAbsolutePath());
                }
            } else {
                if (srcFile.lastModified() > destFile.lastModified()) {
                    filecopyList.put(srcFile.getAbsolutePath(), destFile.getAbsolutePath());
                }
            }
        }
    }

///===///

/**
     * Creates all parent directories specified in a complete relative
     * pathname. Attempts to create existing directories will not cause
     * errors.
     *
     * @param ftp the FTP client instance to use to execute FTP actions on
     *        the remote server.
     * @param filename the name of the file whose parents should be created.
     * @throws IOException under non documented circumstances
     * @throws BuildException if it is impossible to cd to a remote directory
     *
     */
    protected void createParents(FTPClient ftp, String filename)
        throws IOException, BuildException {

        File dir = new File(filename);
        if (dirCache.contains(dir)) {
            return;
        }

        Vector parents = new Vector();
        String dirname;

        while ((dirname = dir.getParent()) != null) {
            File checkDir = new File(dirname);
            if (dirCache.contains(checkDir)) {
                break;
            }
            dir = checkDir;
            parents.addElement(dir);
        }

        // find first non cached dir
        int i = parents.size() - 1;

        if (i >= 0) {
            String cwd = ftp.printWorkingDirectory();
            String parent = dir.getParent();
            if (parent != null) {
                if (!ftp.changeWorkingDirectory(resolveFile(parent))) {
                    throw new BuildException("could not change to "
                                             + "directory: " + ftp.getReplyString());
                }
            }

            while (i >= 0) {
                dir = (File) parents.elementAt(i--);
                // check if dir exists by trying to change into it.
                if (!ftp.changeWorkingDirectory(dir.getName())) {
                    // could not change to it - try to create it
                    log("creating remote directory "
                        + resolveFile(dir.getPath()), Project.MSG_VERBOSE);
                    if (!ftp.makeDirectory(dir.getName())) {
                        handleMkDirFailure(ftp);
                    }
                    if (!ftp.changeWorkingDirectory(dir.getName())) {
                        throw new BuildException("could not change to "
                                                 + "directory: " + ftp.getReplyString());
                    }
                }
                dirCache.add(dir);
            }
            ftp.changeWorkingDirectory(cwd);
        }
    }

///===///

/**
     * Prints a list of all targets in the specified project to
     * <code>System.out</code>, optionally including subtargets.
     *
     * @param project The project to display a description of.
     *                Must not be <code>null</code>.
     * @param printSubTargets Whether or not subtarget names should also be
     *                        printed.
     */
    private static void printTargets(Project project, boolean printSubTargets) {
        // find the target with the longest name
        int maxLength = 0;
        Enumeration ptargets = project.getTargets().elements();
        String targetName;
        String targetDescription;
        Target currentTarget;
        // split the targets in top-level and sub-targets depending
        // on the presence of a description
        Vector topNames = new Vector();
        Vector topDescriptions = new Vector();
        Vector subNames = new Vector();

        while (ptargets.hasMoreElements()) {
            currentTarget = (Target) ptargets.nextElement();
            targetName = currentTarget.getName();
            if (targetName.equals("")) {
                continue;
            }
            targetDescription = currentTarget.getDescription();
            // maintain a sorted list of targets
            if (targetDescription == null) {
                int pos = findTargetPosition(subNames, targetName);
                subNames.insertElementAt(targetName, pos);
            } else {
                int pos = findTargetPosition(topNames, targetName);
                topNames.insertElementAt(targetName, pos);
                topDescriptions.insertElementAt(targetDescription, pos);
                if (targetName.length() > maxLength) {
                    maxLength = targetName.length();
                }
            }
        }

        printTargets(project, topNames, topDescriptions, "Main targets:",
                     maxLength);
        //if there were no main targets, we list all subtargets
        //as it means nothing has a description
        if (topNames.size() == 0) {
            printSubTargets = true;
        }
        if (printSubTargets) {
            printTargets(project, subNames, null, "Other targets:", 0);
        }

        String defaultTarget = project.getDefaultTarget();
        if (defaultTarget != null && !"".equals(defaultTarget)) {
            // shouldn't need to check but...
            project.log("Default target: " + defaultTarget);
        }
    }

///===///

/*
     * A method that does the work on a given entry in a mergefile.
     * The big deal is to set the right parameters in the ZipEntry
     * on the output stream.
     */
    private ZipEntry processEntry(ZipFile zip, ZipEntry inputEntry) {
        /*
          First, some notes.
          On MRJ 2.2.2, getting the size, compressed size, and CRC32 from the
          ZipInputStream does not work for compressed (deflated) files.  Those calls return -1.
          For uncompressed (stored) files, those calls do work.
          However, using ZipFile.getEntries() works for both compressed and
          uncompressed files.

          Now, from some simple testing I did, it seems that the value of CRC-32 is
          independent of the compression setting. So, it should be easy to pass this
          information on to the output entry.
        */
        String name = inputEntry.getName();

        if (!(inputEntry.isDirectory() || name.endsWith(".class"))) {
            try {
                InputStream input = zip.getInputStream(zip.getEntry(name));
                String className = ClassNameReader.getClassName(input);

                input.close();
                if (className != null) {
                    name = className.replace('.', '/') + ".class";
                }
            } catch (IOException ioe) {
                //do nothing
            }
        }
        ZipEntry outputEntry = new ZipEntry(name);

        outputEntry.setTime(inputEntry.getTime());
        outputEntry.setExtra(inputEntry.getExtra());
        outputEntry.setComment(inputEntry.getComment());
        outputEntry.setTime(inputEntry.getTime());
        if (compression) {
            outputEntry.setMethod(ZipEntry.DEFLATED);
            //Note, don't need to specify size or crc for compressed files.
        } else {
            outputEntry.setMethod(ZipEntry.STORED);
            outputEntry.setCrc(inputEntry.getCrc());
            outputEntry.setSize(inputEntry.getSize());
        }
        return outputEntry;
    }

///===///

/**
     * @since Ant 1.6
     */
    JUnitTaskMirror.JUnitResultFormatterMirror createFormatter(ClassLoader loader)
        throws BuildException {

        if (classname == null) {
            throw new BuildException("you must specify type or classname");
        }
        //although this code appears to duplicate that of ClasspathUtils.newInstance,
        //we cannot use that because this formatter may run in a forked process,
        //without that class.
        Class f = null;
        try {
            if (loader == null) {
                f = Class.forName(classname);
            } else {
                f = Class.forName(classname, true, loader);
            }
        } catch (ClassNotFoundException e) {
            throw new BuildException(
                "Using loader " + loader + " on class " + classname
                + ": " + e, e);
        } catch (NoClassDefFoundError e) {
            throw new BuildException(
                "Using loader " + loader + " on class " + classname
                + ": " + e, e);
        }

        Object o = null;
        try {
            o = f.newInstance();
        } catch (InstantiationException e) {
            throw new BuildException(e);
        } catch (IllegalAccessException e) {
            throw new BuildException(e);
        }

        if (!(o instanceof JUnitTaskMirror.JUnitResultFormatterMirror)) {
            throw new BuildException(classname
                + " is not a JUnitResultFormatter");
        }
        JUnitTaskMirror.JUnitResultFormatterMirror r =
            (JUnitTaskMirror.JUnitResultFormatterMirror) o;
        if (useFile && outFile != null) {
            try {
                out = new BufferedOutputStream(new FileOutputStream(outFile));
            } catch (java.io.IOException e) {
                throw new BuildException("Unable to open file " + outFile, e);
            }
        }
        r.setOutput(out);
        return r;
    }

///===///

/**
     * Processes the given input XML file and stores the result
     * in the given resultFile.
     *
     * @param baseDir the base directory for resolving files.
     * @param xmlFile the input file
     * @param destDir the destination directory
     * @param stylesheet the stylesheet to use.
     * @exception BuildException if the processing fails.
     */
    private void process(File baseDir, String xmlFile, File destDir,
                         Resource stylesheet)
        throws BuildException {

        File   outF = null;
        File   inF = null;

        try {
            long styleSheetLastModified = stylesheet.getLastModified();
            inF = new File(baseDir, xmlFile);

            if (inF.isDirectory()) {
                log("Skipping " + inF + " it is a directory.",
                    Project.MSG_VERBOSE);
                return;
            }

            FileNameMapper mapper = null;
            if (mapperElement != null) {
                mapper = mapperElement.getImplementation();
            } else {
                mapper = new StyleMapper();
            }

            String[] outFileName = mapper.mapFileName(xmlFile);
            if (outFileName == null || outFileName.length == 0) {
                log("Skipping " + inFile + " it cannot get mapped to output.",
                    Project.MSG_VERBOSE);
                return;
            } else if (outFileName == null || outFileName.length > 1) {
                log("Skipping " + inFile + " its mapping is ambiguos.",
                    Project.MSG_VERBOSE);
                return;
            }

            outF = new File(destDir, outFileName[0]);

            if (force
                || inF.lastModified() > outF.lastModified()
                || styleSheetLastModified > outF.lastModified()) {
                ensureDirectoryFor(outF);
                log("Processing " + inF + " to " + outF);

                configureLiaison(stylesheet);
                setLiaisonDynamicFileParameters(liaison, inF);
                liaison.transform(inF, outF);
            }
        } catch (Exception ex) {
            // If failed to process document, must delete target document,
            // or it will not attempt to process it the second time
            log("Failed to process " + inFile, Project.MSG_INFO);
            if (outF != null) {
                outF.delete();
            }

            throw new BuildException(ex);
        }

    } //-- processXML

///===///

/**
     * Removes all files and folders not found as keys of a table
     * (used as a set!).
     *
     * <p>If the provided file is a directory, it is recursively
     * scanned for orphaned files which will be removed as well.</p>
     *
     * <p>If the directory is an orphan, it will also be removed.</p>
     *
     * @param  nonOrphans the table of all non-orphan <code>File</code>s.
     * @param  file the initial file or directory to scan or test.
     * @param  preservedDirectories will be filled with the directories
     *         matched by preserveInTarget - if any.  Will not be
     *         filled unless preserveEmptyDirs and includeEmptyDirs
     *         conflict.
     * @return the number of orphaned files and directories actually removed.
     * Position 0 of the array is the number of orphaned directories.
     * Position 1 of the array is the number or orphaned files.
     */
    private int[] removeOrphanFiles(Set nonOrphans, File toDir,
                                    Set preservedDirectories) {
        int[] removedCount = new int[] {0, 0};
        String[] excls =
            (String[]) nonOrphans.toArray(new String[nonOrphans.size() + 1]);
        // want to keep toDir itself
        excls[nonOrphans.size()] = "";

        DirectoryScanner ds = null;
        if (syncTarget != null) {
            FileSet fs = syncTarget.toFileSet(false);
            fs.setDir(toDir);

            // preserveInTarget would find all files we want to keep,
            // but we need to find all that we want to delete - so the
            // meaning of all patterns and selectors must be inverted
            PatternSet ps = syncTarget.mergePatterns(getProject());
            fs.appendExcludes(ps.getIncludePatterns(getProject()));
            fs.appendIncludes(ps.getExcludePatterns(getProject()));
            fs.setDefaultexcludes(!syncTarget.getDefaultexcludes());

            // selectors are implicitly ANDed in DirectoryScanner.  To
            // revert their logic we wrap them into a <none> selector
            // instead.
            FileSelector[] s = syncTarget.getSelectors(getProject());
            if (s.length > 0) {
                NoneSelector ns = new NoneSelector();
                for (int i = 0; i < s.length; i++) {
                    ns.appendSelector(s[i]);
                }
                fs.appendSelector(ns);
            }
            ds = fs.getDirectoryScanner(getProject());
        } else {
            ds = new DirectoryScanner();
            ds.setBasedir(toDir);
        }
        ds.addExcludes(excls);

        ds.scan();
        String[] files = ds.getIncludedFiles();
        for (int i = 0; i < files.length; i++) {
            File f = new File(toDir, files[i]);
            log("Removing orphan file: " + f, Project.MSG_DEBUG);
            f.delete();
            ++removedCount[1];
        }
        String[] dirs = ds.getIncludedDirectories();
        // ds returns the directories in lexicographic order.
        // iterating through the array backwards means we are deleting
        // leaves before their parent nodes - thus making sure (well,
        // more likely) that the directories are empty when we try to
        // delete them.
        for (int i = dirs.length - 1; i >= 0; --i) {
            File f = new File(toDir, dirs[i]);
            String[] children = f.list();
            if (children == null || children.length < 1) {
                log("Removing orphan directory: " + f, Project.MSG_DEBUG);
                f.delete();
                ++removedCount[0];
            }
        }

        Boolean ped = getExplicitPreserveEmptyDirs();
        if (ped != null && ped.booleanValue() != myCopy.getIncludeEmptyDirs()) {
            FileSet fs = syncTarget.toFileSet(true);
            fs.setDir(toDir);
            String[] preservedDirs =
                fs.getDirectoryScanner(getProject()).getIncludedDirectories();
            for (int i = preservedDirs.length - 1; i >= 0; --i) {
                preservedDirectories.add(new File(toDir, preservedDirs[i]));
            }
        }

        return removedCount;
    }

///===///

// We do need to save every possible point, but the number of clone()
    // invocations here is really a killer for performance on non-stingy
    // repeat operators.  I'm open to suggestions...

    // Hypothetical question: can you have a RE that matches 1 times,
    // 3 times, 5 times, but not 2 times or 4 times?  Does having
    // the subexpression back-reference operator allow that?

    boolean match(CharIndexed input, REMatch mymatch) {
    // number of times we've matched so far
    int numRepeats = 0; 
    
    // Possible positions for the next repeat to match at
    REMatch newMatch = mymatch;
    REMatch last = null;
    REMatch current;

    // Add the '0-repeats' index
    // positions.elementAt(z) == position [] in input after <<z>> matches
    Vector positions = new Vector();
    positions.addElement(newMatch);
    
    // Declare variables used in loop
    REMatch doables;
    REMatch doablesLast;
    REMatch recurrent;

    do {
        // Check for stingy match for each possibility.
        if (stingy && (numRepeats >= min)) {
        REMatch result = matchRest(input, newMatch);
        if (result != null) {
            mymatch.assignFrom(result);
            return true;
        }
        }

        doables = null;
        doablesLast = null;

        // try next repeat at all possible positions
        for (current = newMatch; current != null; current = current.next) {
        recurrent = (REMatch) current.clone();
        if (token.match(input, recurrent)) {
            // add all items in current to doables array
            if (doables == null) {
            doables = recurrent;
            doablesLast = recurrent;
            } else {
            // Order these from longest to shortest
            // Start by assuming longest (more repeats)
            doablesLast.next = recurrent;
            }
            // Find new doablesLast
            while (doablesLast.next != null) {
            doablesLast = doablesLast.next;
            }
        }
        }
        // if none of the possibilities worked out, break out of do/while
        if (doables == null) break;
        
        // reassign where the next repeat can match
        newMatch = doables;
        
        // increment how many repeats we've successfully found
        ++numRepeats;
        
        positions.addElement(newMatch);
    } while (numRepeats < max);
    
    // If there aren't enough repeats, then fail
    if (numRepeats < min) return false;
    
    // We're greedy, but ease off until a true match is found 
    int posIndex = positions.size();
    
    // At this point we've either got too many or just the right amount.
    // See if this numRepeats works with the rest of the regexp.
    REMatch allResults = null;
    REMatch allResultsLast = null;

    REMatch results = null;
    while (--posIndex >= min) {
        newMatch = (REMatch) positions.elementAt(posIndex);
        results = matchRest(input, newMatch);
        if (results != null) {
        if (allResults == null) {
            allResults = results;
            allResultsLast = results;
        } else {
            // Order these from longest to shortest
            // Start by assuming longest (more repeats)
            allResultsLast.next = results;
        }
        // Find new doablesLast
        while (allResultsLast.next != null) {
            allResultsLast = allResultsLast.next;
        }
        }
        // else did not match rest of the tokens, try again on smaller sample
    }
    if (allResults != null) {
        mymatch.assignFrom(allResults); // does this get all?
        return true;
    }
    // If we fall out, no matches.
    return false;
    }

///===///

// The meat of construction
  protected void initialize(Object patternObj, int cflags, RESyntax syntax, int myIndex, int nextSub) throws REException {
      char[] pattern;
    if (patternObj instanceof String) {
      pattern = ((String) patternObj).toCharArray();
    } else if (patternObj instanceof char[]) {
      pattern = (char[]) patternObj;
    } else if (patternObj instanceof StringBuffer) {
      pattern = new char [((StringBuffer) patternObj).length()];
      ((StringBuffer) patternObj).getChars(0,pattern.length,pattern,0);
    } else {
    pattern = patternObj.toString().toCharArray();
    }

    int pLength = pattern.length;

    numSubs = 0; // Number of subexpressions in this token.
    Vector branches = null;

    // linked list of tokens (sort of -- some closed loops can exist)
    firstToken = lastToken = null;

    // Precalculate these so we don't pay for the math every time we
    // need to access them.
    boolean insens = ((cflags & REG_ICASE) > 0);

    // Parse pattern into tokens.  Does anyone know if it's more efficient
    // to use char[] than a String.charAt()?  I'm assuming so.

    // index tracks the position in the char array
    int index = 0;

    // this will be the current parse character (pattern[index])
    CharUnit unit = new CharUnit();

    // This is used for {x,y} calculations
    IntPair minMax = new IntPair();

    // Buffer a token so we can create a TokenRepeated, etc.
    REToken currentToken = null;
    char ch;

    while (index < pLength) {
      // read the next character unit (including backslash escapes)
      index = getCharUnit(pattern,index,unit);

      // ALTERNATION OPERATOR
      //  \| or | (if RE_NO_BK_VBAR) or newline (if RE_NEWLINE_ALT)
      //  not available if RE_LIMITED_OPS is set

      // TODO: the '\n' literal here should be a test against REToken.newline,
      // which unfortunately may be more than a single character.
      if ( ( (unit.ch == '|' && (syntax.get(RESyntax.RE_NO_BK_VBAR) ^ unit.bk))
         || (syntax.get(RESyntax.RE_NEWLINE_ALT) && (unit.ch == '\n') && !unit.bk) )
       && !syntax.get(RESyntax.RE_LIMITED_OPS)) {
    // make everything up to here be a branch. create vector if nec.
    addToken(currentToken);
    RE theBranch = new RE(firstToken, lastToken, numSubs, subIndex, minimumLength);
    minimumLength = 0;
    if (branches == null) {
        branches = new Vector();
    }
    branches.addElement(theBranch);
    firstToken = lastToken = currentToken = null;
      }
      
      // INTERVAL OPERATOR:
      //  {x} | {x,} | {x,y}  (RE_INTERVALS && RE_NO_BK_BRACES)
      //  \{x\} | \{x,\} | \{x,y\} (RE_INTERVALS && !RE_NO_BK_BRACES)
      //
      // OPEN QUESTION: 
      //  what is proper interpretation of '{' at start of string?

      else if ((unit.ch == '{') && syntax.get(RESyntax.RE_INTERVALS) && (syntax.get(RESyntax.RE_NO_BK_BRACES) ^ unit.bk)) {
    int newIndex = getMinMax(pattern,index,minMax,syntax);
        if (newIndex > index) {
          if (minMax.first > minMax.second)
            throw new REException(getLocalizedMessage("interval.order"),REException.REG_BADRPT,newIndex);
          if (currentToken == null)
            throw new REException(getLocalizedMessage("repeat.no.token"),REException.REG_BADRPT,newIndex);
          if (currentToken instanceof RETokenRepeated) 
            throw new REException(getLocalizedMessage("repeat.chained"),REException.REG_BADRPT,newIndex);
          if (currentToken instanceof RETokenWordBoundary || currentToken instanceof RETokenWordBoundary)
            throw new REException(getLocalizedMessage("repeat.assertion"),REException.REG_BADRPT,newIndex);
          if ((currentToken.getMinimumLength() == 0) && (minMax.second == Integer.MAX_VALUE))
            throw new REException(getLocalizedMessage("repeat.empty.token"),REException.REG_BADRPT,newIndex);
          index = newIndex;
          currentToken = setRepeated(currentToken,minMax.first,minMax.second,index); 
        }
        else {
          addToken(currentToken);
          currentToken = new RETokenChar(subIndex,unit.ch,insens);
        } 
      }
      
      // LIST OPERATOR:
      //  [...] | [^...]

      else if ((unit.ch == '[') && !unit.bk) {
    Vector options = new Vector();
    boolean negative = false;
    char lastChar = 0;
    if (index == pLength) throw new REException(getLocalizedMessage("unmatched.bracket"),REException.REG_EBRACK,index);
    
    // Check for initial caret, negation
    if ((ch = pattern[index]) == '^') {
      negative = true;
      if (++index == pLength) throw new REException(getLocalizedMessage("class.no.end"),REException.REG_EBRACK,index);
      ch = pattern[index];
    }

    // Check for leading right bracket literal
    if (ch == ']') {
      lastChar = ch;
      if (++index == pLength) throw new REException(getLocalizedMessage("class.no.end"),REException.REG_EBRACK,index);
    }

    while ((ch = pattern[index++]) != ']') {
      if ((ch == '-') && (lastChar != 0)) {
        if (index == pLength) throw new REException(getLocalizedMessage("class.no.end"),REException.REG_EBRACK,index);
        if ((ch = pattern[index]) == ']') {
          options.addElement(new RETokenChar(subIndex,lastChar,insens));
          lastChar = '-';
        } else {
          options.addElement(new RETokenRange(subIndex,lastChar,ch,insens));
          lastChar = 0;
          index++;
        }
          } else if ((ch == '\\') && syntax.get(RESyntax.RE_BACKSLASH_ESCAPE_IN_LISTS)) {
            if (index == pLength) throw new REException(getLocalizedMessage("class.no.end"),REException.REG_EBRACK,index);
        int posixID = -1;
        boolean negate = false;
            char asciiEsc = 0;
        if (("dswDSW".indexOf(pattern[index]) != -1) && syntax.get(RESyntax.RE_CHAR_CLASS_ESC_IN_LISTS)) {
          switch (pattern[index]) {
          case 'D':
        negate = true;
          case 'd':
        posixID = RETokenPOSIX.DIGIT;
        break;
          case 'S':
        negate = true;
          case 's':
        posixID = RETokenPOSIX.SPACE;
        break;
          case 'W':
        negate = true;
          case 'w':
        posixID = RETokenPOSIX.ALNUM;
        break;
          }
        }
            else if ("nrt".indexOf(pattern[index]) != -1) {
              switch (pattern[index]) {
                case 'n':
                  asciiEsc = '\n';
                  break;
                case 't':
                  asciiEsc = '\t';
                  break;
                case 'r':
                  asciiEsc = '\r';
                  break;
              }
            }
        if (lastChar != 0) options.addElement(new RETokenChar(subIndex,lastChar,insens));
        
        if (posixID != -1) {
          options.addElement(new RETokenPOSIX(subIndex,posixID,insens,negate));
        } else if (asciiEsc != 0) {
          lastChar = asciiEsc;
        } else {
          lastChar = pattern[index];
        }
        ++index;
      } else if ((ch == '[') && (syntax.get(RESyntax.RE_CHAR_CLASSES)) && (index < pLength) && (pattern[index] == ':')) {
        StringBuffer posixSet = new StringBuffer();
        index = getPosixSet(pattern,index+1,posixSet);
        int posixId = RETokenPOSIX.intValue(posixSet.toString());
        if (posixId != -1)
          options.addElement(new RETokenPOSIX(subIndex,posixId,insens,false));
      } else {
        if (lastChar != 0) options.addElement(new RETokenChar(subIndex,lastChar,insens));
        lastChar = ch;
      }
      if (index == pLength) throw new REException(getLocalizedMessage("class.no.end"),REException.REG_EBRACK,index);
    } // while in list
    // Out of list, index is one past ']'
        
    if (lastChar != 0) options.addElement(new RETokenChar(subIndex,lastChar,insens));
        
    // Create a new RETokenOneOf
    addToken(currentToken);
    options.trimToSize();
    currentToken = new RETokenOneOf(subIndex,options,negative);
      }

      // SUBEXPRESSIONS
      //  (...) | \(...\) depending on RE_NO_BK_PARENS

      else if ((unit.ch == '(') && (syntax.get(RESyntax.RE_NO_BK_PARENS) ^ unit.bk)) {
    boolean pure = false;
    boolean comment = false;
        boolean lookAhead = false;
        boolean negativelh = false;
    if ((index+1 < pLength) && (pattern[index] == '?')) {
      switch (pattern[index+1]) {
          case '!':
            if (syntax.get(RESyntax.RE_LOOKAHEAD)) {
              pure = true;
              negativelh = true;
              lookAhead = true;
              index += 2;
            }
            break;
          case '=':
            if (syntax.get(RESyntax.RE_LOOKAHEAD)) {
              pure = true;
              lookAhead = true;
              index += 2;
            }
            break;
      case ':':
        if (syntax.get(RESyntax.RE_PURE_GROUPING)) {
          pure = true;
          index += 2;
        }
        break;
      case '#':
        if (syntax.get(RESyntax.RE_COMMENTS)) {
          comment = true;
        }
        break;
          default:
            throw new REException(getLocalizedMessage("repeat.no.token"), REException.REG_BADRPT, index);
      }
    }

    if (index >= pLength) {
        throw new REException(getLocalizedMessage("unmatched.paren"), REException.REG_ESUBREG,index);
    }

    // find end of subexpression
    int endIndex = index;
    int nextIndex = index;
    int nested = 0;

    while ( ((nextIndex = getCharUnit(pattern,endIndex,unit)) > 0)
        && !(nested == 0 && (unit.ch == ')') && (syntax.get(RESyntax.RE_NO_BK_PARENS) ^ unit.bk)) )
      if ((endIndex = nextIndex) >= pLength)
        throw new REException(getLocalizedMessage("subexpr.no.end"),REException.REG_ESUBREG,nextIndex);
      else if (unit.ch == '(' && (syntax.get(RESyntax.RE_NO_BK_PARENS) ^ unit.bk))
        nested++;
      else if (unit.ch == ')' && (syntax.get(RESyntax.RE_NO_BK_PARENS) ^ unit.bk))
        nested--;

    // endIndex is now position at a ')','\)' 
    // nextIndex is end of string or position after ')' or '\)'

    if (comment) index = nextIndex;
    else { // not a comment
      // create RE subexpression as token.
      addToken(currentToken);
      if (!pure) {
        numSubs++;
      }

      int useIndex = (pure || lookAhead) ? 0 : nextSub + numSubs;
      currentToken = new RE(String.valueOf(pattern,index,endIndex-index).toCharArray(),cflags,syntax,useIndex,nextSub + numSubs);
      numSubs += ((RE) currentToken).getNumSubs();

          if (lookAhead) {
          currentToken = new RETokenLookAhead(currentToken,negativelh);
      }

      index = nextIndex;
    } // not a comment
      } // subexpression
    
      // UNMATCHED RIGHT PAREN
      // ) or \) throw exception if
      // !syntax.get(RESyntax.RE_UNMATCHED_RIGHT_PAREN_ORD)
      else if (!syntax.get(RESyntax.RE_UNMATCHED_RIGHT_PAREN_ORD) && ((unit.ch == ')') && (syntax.get(RESyntax.RE_NO_BK_PARENS) ^ unit.bk))) {
    throw new REException(getLocalizedMessage("unmatched.paren"),REException.REG_EPAREN,index);
      }

      // START OF LINE OPERATOR
      //  ^

      else if ((unit.ch == '^') && !unit.bk) {
    addToken(currentToken);
    currentToken = null;
    addToken(new RETokenStart(subIndex,((cflags & REG_MULTILINE) > 0) ? syntax.getLineSeparator() : null));
      }

      // END OF LINE OPERATOR
      //  $

      else if ((unit.ch == '$') && !unit.bk) {
    addToken(currentToken);
    currentToken = null;
    addToken(new RETokenEnd(subIndex,((cflags & REG_MULTILINE) > 0) ? syntax.getLineSeparator() : null));
      }

      // MATCH-ANY-CHARACTER OPERATOR (except possibly newline and null)
      //  .

      else if ((unit.ch == '.') && !unit.bk) {
    addToken(currentToken);
    currentToken = new RETokenAny(subIndex,syntax.get(RESyntax.RE_DOT_NEWLINE) || ((cflags & REG_DOT_NEWLINE) > 0),syntax.get(RESyntax.RE_DOT_NOT_NULL));
      }

      // ZERO-OR-MORE REPEAT OPERATOR
      //  *

      else if ((unit.ch == '*') && !unit.bk) {
    if (currentToken == null)
          throw new REException(getLocalizedMessage("repeat.no.token"),REException.REG_BADRPT,index);
    if (currentToken instanceof RETokenRepeated)
          throw new REException(getLocalizedMessage("repeat.chained"),REException.REG_BADRPT,index);
    if (currentToken instanceof RETokenWordBoundary || currentToken instanceof RETokenWordBoundary)
      throw new REException(getLocalizedMessage("repeat.assertion"),REException.REG_BADRPT,index);
    if (currentToken.getMinimumLength() == 0)
      throw new REException(getLocalizedMessage("repeat.empty.token"),REException.REG_BADRPT,index);
    currentToken = setRepeated(currentToken,0,Integer.MAX_VALUE,index);
      }

      // ONE-OR-MORE REPEAT OPERATOR
      //  + | \+ depending on RE_BK_PLUS_QM
      //  not available if RE_LIMITED_OPS is set

      else if ((unit.ch == '+') && !syntax.get(RESyntax.RE_LIMITED_OPS) && (!syntax.get(RESyntax.RE_BK_PLUS_QM) ^ unit.bk)) {
    if (currentToken == null)
          throw new REException(getLocalizedMessage("repeat.no.token"),REException.REG_BADRPT,index);
    if (currentToken instanceof RETokenRepeated)
          throw new REException(getLocalizedMessage("repeat.chained"),REException.REG_BADRPT,index);
    if (currentToken instanceof RETokenWordBoundary || currentToken instanceof RETokenWordBoundary)
      throw new REException(getLocalizedMessage("repeat.assertion"),REException.REG_BADRPT,index);
    if (currentToken.getMinimumLength() == 0)
      throw new REException(getLocalizedMessage("repeat.empty.token"),REException.REG_BADRPT,index);
    currentToken = setRepeated(currentToken,1,Integer.MAX_VALUE,index);
      }

      // ZERO-OR-ONE REPEAT OPERATOR / STINGY MATCHING OPERATOR
      //  ? | \? depending on RE_BK_PLUS_QM
      //  not available if RE_LIMITED_OPS is set
      //  stingy matching if RE_STINGY_OPS is set and it follows a quantifier

      else if ((unit.ch == '?') && !syntax.get(RESyntax.RE_LIMITED_OPS) && (!syntax.get(RESyntax.RE_BK_PLUS_QM) ^ unit.bk)) {
    if (currentToken == null) throw new REException(getLocalizedMessage("repeat.no.token"),REException.REG_BADRPT,index);

    // Check for stingy matching on RETokenRepeated
    if (currentToken instanceof RETokenRepeated) {
          if (syntax.get(RESyntax.RE_STINGY_OPS) && !((RETokenRepeated)currentToken).isStingy())
            ((RETokenRepeated)currentToken).makeStingy();
          else
            throw new REException(getLocalizedMessage("repeat.chained"),REException.REG_BADRPT,index);
        }
        else if (currentToken instanceof RETokenWordBoundary || currentToken instanceof RETokenWordBoundary)
          throw new REException(getLocalizedMessage("repeat.assertion"),REException.REG_BADRPT,index);
    else
      currentToken = setRepeated(currentToken,0,1,index);
      }
    
      // BACKREFERENCE OPERATOR
      //  \1 \2 ... \9
      // not available if RE_NO_BK_REFS is set

      else if (unit.bk && Character.isDigit(unit.ch) && !syntax.get(RESyntax.RE_NO_BK_REFS)) {
    addToken(currentToken);
    currentToken = new RETokenBackRef(subIndex,Character.digit(unit.ch,10),insens);
      }

      // START OF STRING OPERATOR
      //  \A if RE_STRING_ANCHORS is set
      
      else if (unit.bk && (unit.ch == 'A') && syntax.get(RESyntax.RE_STRING_ANCHORS)) {
    addToken(currentToken);
    currentToken = new RETokenStart(subIndex,null);
      }

      // WORD BREAK OPERATOR
      //  \b if ????

      else if (unit.bk && (unit.ch == 'b') && syntax.get(RESyntax.RE_STRING_ANCHORS)) {
      addToken(currentToken);
      currentToken = new RETokenWordBoundary(subIndex, RETokenWordBoundary.BEGIN | RETokenWordBoundary.END, false);
      } 

      // WORD BEGIN OPERATOR 
      //  \< if ????
      else if (unit.bk && (unit.ch == '<')) {
      addToken(currentToken);
      currentToken = new RETokenWordBoundary(subIndex, RETokenWordBoundary.BEGIN, false);
      } 

      // WORD END OPERATOR 
      //  \> if ????
      else if (unit.bk && (unit.ch == '>')) {
      addToken(currentToken);
      currentToken = new RETokenWordBoundary(subIndex, RETokenWordBoundary.END, false);
      } 

      // NON-WORD BREAK OPERATOR
      // \B if ????

      else if (unit.bk && (unit.ch == 'B') && syntax.get(RESyntax.RE_STRING_ANCHORS)) {
      addToken(currentToken);
      currentToken = new RETokenWordBoundary(subIndex, RETokenWordBoundary.BEGIN | RETokenWordBoundary.END, true);
      } 

      
      // DIGIT OPERATOR
      //  \d if RE_CHAR_CLASS_ESCAPES is set
      
      else if (unit.bk && (unit.ch == 'd') && syntax.get(RESyntax.RE_CHAR_CLASS_ESCAPES)) {
    addToken(currentToken);
    currentToken = new RETokenPOSIX(subIndex,RETokenPOSIX.DIGIT,insens,false);
      }

      // NON-DIGIT OPERATOR
      //  \D

    else if (unit.bk && (unit.ch == 'D') && syntax.get(RESyntax.RE_CHAR_CLASS_ESCAPES)) {
      addToken(currentToken);
      currentToken = new RETokenPOSIX(subIndex,RETokenPOSIX.DIGIT,insens,true);
    }

    // NEWLINE ESCAPE
        //  \n

    else if (unit.bk && (unit.ch == 'n')) {
      addToken(currentToken);
      currentToken = new RETokenChar(subIndex,'\n',false);
    }

    // RETURN ESCAPE
        //  \r

    else if (unit.bk && (unit.ch == 'r')) {
      addToken(currentToken);
      currentToken = new RETokenChar(subIndex,'\r',false);
    }

    // WHITESPACE OPERATOR
        //  \s if RE_CHAR_CLASS_ESCAPES is set

    else if (unit.bk && (unit.ch == 's') && syntax.get(RESyntax.RE_CHAR_CLASS_ESCAPES)) {
      addToken(currentToken);
      currentToken = new RETokenPOSIX(subIndex,RETokenPOSIX.SPACE,insens,false);
    }

    // NON-WHITESPACE OPERATOR
        //  \S

    else if (unit.bk && (unit.ch == 'S') && syntax.get(RESyntax.RE_CHAR_CLASS_ESCAPES)) {
      addToken(currentToken);
      currentToken = new RETokenPOSIX(subIndex,RETokenPOSIX.SPACE,insens,true);
    }

    // TAB ESCAPE
        //  \t

    else if (unit.bk && (unit.ch == 't')) {
      addToken(currentToken);
      currentToken = new RETokenChar(subIndex,'\t',false);
    }

    // ALPHANUMERIC OPERATOR
        //  \w

    else if (unit.bk && (unit.ch == 'w') && syntax.get(RESyntax.RE_CHAR_CLASS_ESCAPES)) {
      addToken(currentToken);
      currentToken = new RETokenPOSIX(subIndex,RETokenPOSIX.ALNUM,insens,false);
    }

    // NON-ALPHANUMERIC OPERATOR
        //  \W

    else if (unit.bk && (unit.ch == 'W') && syntax.get(RESyntax.RE_CHAR_CLASS_ESCAPES)) {
      addToken(currentToken);
      currentToken = new RETokenPOSIX(subIndex,RETokenPOSIX.ALNUM,insens,true);
    }

    // END OF STRING OPERATOR
        //  \Z

    else if (unit.bk && (unit.ch == 'Z') && syntax.get(RESyntax.RE_STRING_ANCHORS)) {
      addToken(currentToken);
      currentToken = new RETokenEnd(subIndex,null);
    }

    // NON-SPECIAL CHARACTER (or escape to make literal)
        //  c | \* for example

    else {  // not a special character
      addToken(currentToken);
      currentToken = new RETokenChar(subIndex,unit.ch,insens);
    } 
      } // end while

    // Add final buffered token and an EndSub marker
    addToken(currentToken);
      
    if (branches != null) {
    branches.addElement(new RE(firstToken,lastToken,numSubs,subIndex,minimumLength));
    branches.trimToSize(); // compact the Vector
    minimumLength = 0;
    firstToken = lastToken = null;
    addToken(new RETokenOneOf(subIndex,branches,false));
    } 
    else addToken(new RETokenEndSub(subIndex));

  }

///===///

/**
     * The main test routine.
     * @param argv Command line parameters.
     * <br>
     * This method expects a conventional command line that will be passed as
     * a parameter string to the Windows executable <code>jcmd.exe</code>.
     */
    public static void main(String argv[])
    {
        System.out.println("Testing JcmdProcess class.");
        String cmdLine = null;
        JcmdProcess process = null;
        /* Get full command line */
        if(argv.length > 0)
        {
            StringBuffer sb = new StringBuffer(argv[0]);
            for(int i = 1; i < argv.length; ++i)
            {
                sb.append(' ').append(argv[i]);
            }
            cmdLine = sb.toString();
            System.out.println("Command line: " + cmdLine);
        }
        else
        {
            System.err.println("No command line provided.");
            System.exit(1);
        }
        try
        {
            process = JcmdProcess.exec(cmdLine, null);
            BufferedReader pInput = new BufferedReader(
                                        new InputStreamReader(
                                        process.getInputStream()));
            String s = pInput.readLine();
            /* call sendBreak after fixed number of
             * lines of stdout are read
             */
            int nRead = 0;
            while (s != null)
            {
                System.out.println(s);
                if(++nRead == 5)
                    process.sendBreak();
                s = pInput.readLine();
            }
        }
        catch(IOException e)
        {
            System.err.println("I/O error: " + e.toString());
        }
        catch(Exception e)
        {
            System.err.println("JcmdProcess error: " + e.toString());
        }
        System.out.println("Child process exited with code " +
            String.valueOf(process.exitValue()));
        System.exit(0);
    }
}

///===///

//{{{ autodetect() method
    /**
     * Tries to detect if the stream is gzipped, and if it has an encoding
     * specified with an XML PI.
     */
    private Reader autodetect(InputStream in) throws IOException
    {
        in = new BufferedInputStream(in);

        String encoding = buffer.getStringProperty(Buffer.ENCODING);
        if(!in.markSupported())
            Log.log(Log.WARNING,this,"Mark not supported: " + in);
        else if(buffer.getBooleanProperty(Buffer.ENCODING_AUTODETECT))
        {
            in.mark(XML_PI_LENGTH);
            int b1 = in.read();
            int b2 = in.read();
            int b3 = in.read();

            if(encoding.equals(MiscUtilities.UTF_8_Y))
            {
                // Java does not support this encoding so
                // we have to handle it manually.
                if(b1 != UTF8_MAGIC_1 || b2 != UTF8_MAGIC_2
                    || b3 != UTF8_MAGIC_3)
                {
                    // file does not begin with UTF-8-Y
                    // signature. reset stream, read as
                    // UTF-8.
                    in.reset();
                }
                else
                {
                    // file begins with UTF-8-Y signature.
                    // discard the signature, and read
                    // the remainder as UTF-8.
                }

                encoding = "UTF-8";
            }
            else if(b1 == GZIP_MAGIC_1 && b2 == GZIP_MAGIC_2)
            {
                in.reset();
                in = new GZIPInputStream(in);
                buffer.setBooleanProperty(Buffer.GZIPPED,true);
                // auto-detect encoding within the gzip stream.
                return autodetect(in);
            }
            else if((b1 == UNICODE_MAGIC_1
                && b2 == UNICODE_MAGIC_2)
                || (b1 == UNICODE_MAGIC_2
                && b2 == UNICODE_MAGIC_1))
            {
                in.reset();
                encoding = "UTF-16";
                buffer.setProperty(Buffer.ENCODING,encoding);
            }
            else if(b1 == UTF8_MAGIC_1 && b2 == UTF8_MAGIC_2
                && b3 == UTF8_MAGIC_3)
            {
                // do not reset the stream and just treat it
                // like a normal UTF-8 file.
                buffer.setProperty(Buffer.ENCODING,
                    MiscUtilities.UTF_8_Y);

                encoding = "UTF-8";
            }
            else
            {
                in.reset();

                byte[] _xmlPI = new byte[XML_PI_LENGTH];
                int offset = 0;
                int count;
                while((count = in.read(_xmlPI,offset,
                    XML_PI_LENGTH - offset)) != -1)
                {
                    offset += count;
                    if(offset == XML_PI_LENGTH)
                        break;
                }

                String xmlPI = new String(_xmlPI,0,offset,
                "ASCII");
                if(xmlPI.startsWith("<?xml"))
                {
                    int index = xmlPI.indexOf("encoding=");
                    if(index != -1
                        && index + 9 != xmlPI.length())
                    {
                        char ch = xmlPI.charAt(index
                        + 9);
                        int endIndex = xmlPI.indexOf(ch,
                            index + 10);
                        encoding = xmlPI.substring(
                            index + 10,endIndex);
    
                        if(MiscUtilities.isSupportedEncoding(encoding))
                        {
                            buffer.setProperty(Buffer.ENCODING,encoding);
                        }
                        else
                        {
                            Log.log(Log.WARNING,this,"XML PI specifies unsupported encoding: " + encoding);
                        }
                    }
                }

                in.reset();
            }
        }

        return new InputStreamReader(in,encoding);
    } //}}}

///===///

//{{{ handleClient() method
    /**
     * @param restore Ignored unless no views are open
     * @param newView Open a new view?
     * @param newPlainView Open a new plain view?
     * @param parent The client's parent directory
     * @param args A list of files. Null entries are ignored, for convinience
     * @since jEdit 4.2pre1
     */
    public static Buffer handleClient(boolean restore,
        boolean newView, boolean newPlainView, String parent,
        String[] args)
    {
        // we have to deal with a huge range of possible border cases here.
        if(jEdit.getFirstView() == null)
        {
            // coming out of background mode.
            // no views open.
            // no buffers open if args empty.

            Buffer buffer = jEdit.openFiles(null,parent,args);

            if(jEdit.getBufferCount() == 0)
                jEdit.newFile(null);

            boolean restoreFiles = restore
                && jEdit.getBooleanProperty("restore")
                && (buffer == null
                || jEdit.getBooleanProperty("restore.cli"));

            View view = PerspectiveManager.loadPerspective(
                restoreFiles);

            if(view == null)
            {
                if(buffer == null)
                    buffer = jEdit.getFirstBuffer();
                view = jEdit.newView(null,buffer);
            }
            else if(buffer != null)
                view.setBuffer(buffer);

            return buffer;
        }
        else if(newPlainView)
        {
            // no background mode, and opening a new view
            Buffer buffer = jEdit.openFiles(null,parent,args);
            if(buffer == null)
                buffer = jEdit.getFirstBuffer();
            jEdit.newView(null,buffer,true);
            return buffer;
        }
        else if(newView)
        {
            // no background mode, and opening a new view
            Buffer buffer = jEdit.openFiles(null,parent,args);
            if(buffer == null)
                buffer = jEdit.getFirstBuffer();
            jEdit.newView(jEdit.getActiveView(),buffer,false);
            return buffer;
        }
        else
        {
            // no background mode, and reusing existing view
            View view = jEdit.getActiveView();

            Buffer buffer = jEdit.openFiles(view,parent,args);

            // Hack done to fix bringing the window to the front.
            // At least on windows, Frame.toFront() doesn't cut it.
            // Remove the isWindows check if it's broken under other
            // OSes too.
            if (jEdit.getBooleanProperty("server.brokenToFront"))
                view.setState(java.awt.Frame.ICONIFIED);

            // un-iconify using JDK 1.3 API
            view.setState(java.awt.Frame.NORMAL);
            view.requestFocus();
            view.toFront();

            return buffer;
        }
    } //}}}

///===///

//{{{ register() method
    public void register(final DockableWindowManager.Entry entry)
    {
        dockables.add(entry);

        //{{{ Create button
        int rotation;
        if(position.equals(DockableWindowManager.TOP)
            || position.equals(DockableWindowManager.BOTTOM))
            rotation = RotatedTextIcon.NONE;
        else if(position.equals(DockableWindowManager.LEFT))
            rotation = RotatedTextIcon.CCW;
        else if(position.equals(DockableWindowManager.RIGHT))
            rotation = RotatedTextIcon.CW;
        else
            throw new InternalError("Invalid position: " + position);

        JToggleButton button = new JToggleButton();
        button.setMargin(new Insets(1,1,1,1));
        button.setRequestFocusEnabled(false);
        button.setIcon(new RotatedTextIcon(rotation,button.getFont(),
            entry.title));
        button.setActionCommand(entry.factory.name);
        button.addActionListener(new ActionHandler());
        button.addMouseListener(new MenuMouseHandler());
        if(OperatingSystem.isMacOSLF())
            button.putClientProperty("JButton.buttonType","toolbar");
        //}}}

        buttonGroup.add(button);
        buttons.add(button);
        entry.btn = button;

        wm.revalidate();
    } //}}}

///===///

    //{{{ load() method
    /**
     * Loads the buffer from disk, even if it is loaded already.
     * @param view The view
     * @param reload If true, user will not be asked to recover autosave
     * file, if any
     *
     * @since 2.5pre1
     */
    public boolean load(final View view, final boolean reload)
    {
        if(isPerformingIO())
        {
            GUIUtilities.error(view,"buffer-multiple-io",null);
            return false;
        }

        setBooleanProperty(BufferIORequest.ERROR_OCCURRED,false);

        setFlag(LOADING,true);

        // view text areas temporarily blank out while a buffer is
        // being loaded, to indicate to the user that there is no
        // data available yet.
        if(!getFlag(TEMPORARY))
            EditBus.send(new BufferUpdate(this,view,BufferUpdate.LOAD_STARTED));

        final boolean loadAutosave;

        if(reload || !getFlag(NEW_FILE))
        {
            if(file != null)
                modTime = file.lastModified();

            // Only on initial load
            if(!reload && autosaveFile != null && autosaveFile.exists())
                loadAutosave = recoverAutosave(view);
            else
            {
                if(autosaveFile != null)
                    autosaveFile.delete();
                loadAutosave = false;
            }

            if(!loadAutosave)
            {
                VFS vfs = VFSManager.getVFSForPath(path);

                if(!checkFileForLoad(view,vfs,path))
                {
                    setFlag(LOADING,false);
                    return false;
                }

                // have to check again since above might set
                // NEW_FILE flag
                if(reload || !getFlag(NEW_FILE))
                {
                    if(!vfs.load(view,this,path))
                    {
                        setFlag(LOADING,false);
                        return false;
                    }
                }
            }
        }
        else
            loadAutosave = false;

        //{{{ Do some stuff once loading is finished
        Runnable runnable = new Runnable()
        {
            public void run()
            {
                String newPath = getStringProperty(
                    BufferIORequest.NEW_PATH);
                Segment seg = (Segment)getProperty(
                    BufferIORequest.LOAD_DATA);
                IntegerArray endOffsets = (IntegerArray)
                    getProperty(BufferIORequest.END_OFFSETS);

                if(seg == null)
                    seg = new Segment(new char[1024],0,0);
                if(endOffsets == null)
                {
                    endOffsets = new IntegerArray();
                    endOffsets.add(1);
                }

                try
                {
                    writeLock();

                    // For `reload' command
                    firePreContentRemoved(0,0,getLineCount()
                        - 1,getLength());

                    contentMgr.remove(0,getLength());
                    lineMgr.contentRemoved(0,0,getLineCount()
                        - 1,getLength());
                    positionMgr.contentRemoved(0,getLength());
                    fireContentRemoved(0,0,getLineCount()
                        - 1,getLength());

                    // theoretically a segment could
                    // have seg.offset != 0 but
                    // SegmentBuffer never does that
                    contentMgr._setContent(seg.array,seg.count);

                    lineMgr._contentInserted(endOffsets);
                    positionMgr.contentInserted(0,seg.count);

                    fireContentInserted(0,0,
                        endOffsets.getSize() - 1,
                        seg.count - 1);
                }
                finally
                {
                    writeUnlock();
                }

                unsetProperty(BufferIORequest.LOAD_DATA);
                unsetProperty(BufferIORequest.END_OFFSETS);
                unsetProperty(BufferIORequest.NEW_PATH);

                undoMgr.clear();
                undoMgr.setLimit(jEdit.getIntegerProperty(
                    "buffer.undoCount",100));

                if(!getFlag(TEMPORARY))
                    finishLoading();

                setFlag(LOADING,false);

                // if reloading a file, clear dirty flag
                if(reload)
                    setDirty(false);

                if(!loadAutosave && newPath != null)
                    setPath(newPath);

                // if loadAutosave is false, we loaded an
                // autosave file, so we set 'dirty' to true

                // note that we don't use setDirty(),
                // because a) that would send an unnecessary
                // message, b) it would also set the
                // AUTOSAVE_DIRTY flag, which will make
                // the autosave thread write out a
                // redundant autosave file
                if(loadAutosave)
                    setFlag(DIRTY,true);

                // send some EditBus messages
                if(!getFlag(TEMPORARY))
                {
                    EditBus.send(new BufferUpdate(Buffer.this,
                        view,BufferUpdate.LOADED));
                    //EditBus.send(new BufferUpdate(Buffer.this,
                    //  view,BufferUpdate.MARKERS_CHANGED));
                }
            }
        }; //}}}

        if(getFlag(TEMPORARY))
            runnable.run();
        else
            VFSManager.runInAWTThread(runnable);

        return true;
    } //}}}

///===///

//{{{ layoutContainer() method
        public void layoutContainer(Container parent)
        {
            Dimension size = parent.getSize();

            Dimension _topToolbars = (topToolbars == null
                ? new Dimension(0,0)
                : topToolbars.getPreferredSize());
            Dimension _bottomToolbars = (bottomToolbars == null
                ? new Dimension(0,0)
                : bottomToolbars.getPreferredSize());

            int topButtonHeight = -1;
            int bottomButtonHeight = -1;
            int leftButtonWidth = -1;
            int rightButtonWidth = -1;

            Dimension _top = top.getPreferredSize();
            Dimension _left = left.getPreferredSize();
            Dimension _bottom = bottom.getPreferredSize();
            Dimension _right = right.getPreferredSize();

            int topHeight = _top.height;
            int bottomHeight = _bottom.height;
            int leftWidth = _left.width;
            int rightWidth = _right.width;

            boolean topEmpty = ((Container)topButtons)
                .getComponentCount() <= 2;
            boolean leftEmpty = ((Container)leftButtons)
                .getComponentCount() <= 2;
            boolean bottomEmpty = ((Container)bottomButtons)
                .getComponentCount() <= 2;
            boolean rightEmpty = ((Container)rightButtons)
                .getComponentCount() <= 2;

            Dimension closeBoxSize;
            if(((Container)topButtons).getComponentCount() == 0)
                closeBoxSize = new Dimension(0,0);
            else
            {
                closeBoxSize = ((Container)topButtons)
                    .getComponent(0).getPreferredSize();
            }

            int closeBoxWidth = Math.max(closeBoxSize.width,
                closeBoxSize.height) + 1;

            if(alternateLayout)
            {
                //{{{ Lay out independent buttons
                int _width = size.width;

                int padding = (leftEmpty&&rightEmpty)
                    ? 0 : closeBoxWidth;

                topButtonHeight = DockableWindowManager.this.
                    top.getWrappedDimension(_width
                    - closeBoxWidth * 2);
                topButtons.setBounds(
                    padding,
                    0,
                    size.width - padding * 2,
                    topButtonHeight);

                bottomButtonHeight = DockableWindowManager.this.
                    bottom.getWrappedDimension(_width);
                bottomButtons.setBounds(
                    padding,
                    size.height - bottomButtonHeight,
                    size.width - padding * 2,
                    bottomButtonHeight);

                int _height = size.height
                    - topButtonHeight
                    - bottomButtonHeight;
                //}}}

                //{{{ Lay out dependent buttons
                leftButtonWidth = DockableWindowManager.this.
                    left.getWrappedDimension(_height);
                leftButtons.setBounds(
                    0,
                    topHeight + topButtonHeight,
                    leftButtonWidth,
                    _height - topHeight - bottomHeight);

                rightButtonWidth = DockableWindowManager.this.
                    right.getWrappedDimension(_height);
                rightButtons.setBounds(
                    size.width - rightButtonWidth,
                    topHeight + topButtonHeight,
                    rightButtonWidth,
                    _height - topHeight - bottomHeight);
                //}}}

                int[] dimensions = adjustDockingAreasToFit(
                    size,
                    topHeight,
                    leftWidth,
                    bottomHeight,
                    rightWidth,
                    topButtonHeight,
                    leftButtonWidth,
                    bottomButtonHeight,
                    rightButtonWidth,
                    _topToolbars,
                    _bottomToolbars);

                topHeight = dimensions[0];
                leftWidth = dimensions[1];
                bottomHeight = dimensions[2];
                rightWidth = dimensions[3];

                //{{{ Lay out docking areas
                top.setBounds(
                    0,
                    topButtonHeight,
                    size.width,
                    topHeight);

                bottom.setBounds(
                    0,
                    size.height
                    - bottomHeight
                    - bottomButtonHeight,
                    size.width,
                    bottomHeight);

                left.setBounds(
                    leftButtonWidth,
                    topButtonHeight + topHeight,
                    leftWidth,
                    _height - topHeight - bottomHeight);

                right.setBounds(
                    _width - rightButtonWidth - rightWidth,
                    topButtonHeight + topHeight,
                    rightWidth,
                    _height - topHeight - bottomHeight); //}}}
            }
            else
            {
                //{{{ Lay out independent buttons
                int _height = size.height;

                int padding = (topEmpty && bottomEmpty
                    ? 0 : closeBoxWidth);

                leftButtonWidth = DockableWindowManager.this.
                    left.getWrappedDimension(_height
                    - closeBoxWidth * 2);
                leftButtons.setBounds(
                    0,
                    padding,
                    leftButtonWidth,
                    _height - padding * 2);

                rightButtonWidth = DockableWindowManager.this.
                    right.getWrappedDimension(_height);
                rightButtons.setBounds(
                    size.width - rightButtonWidth,
                    padding,
                    rightButtonWidth,
                    _height - padding * 2);

                int _width = size.width
                    - leftButtonWidth
                    - rightButtonWidth;
                //}}}

                //{{{ Lay out dependent buttons
                topButtonHeight = DockableWindowManager.this.
                    top.getWrappedDimension(_width);
                topButtons.setBounds(
                    leftButtonWidth + leftWidth,
                    0,
                    _width - leftWidth - rightWidth,
                    topButtonHeight);

                bottomButtonHeight = DockableWindowManager.this.
                    bottom.getWrappedDimension(_width);
                bottomButtons.setBounds(
                    leftButtonWidth + leftWidth,
                    _height - bottomButtonHeight,
                    _width - leftWidth - rightWidth,
                    bottomButtonHeight); //}}}

                int[] dimensions = adjustDockingAreasToFit(
                    size,
                    topHeight,
                    leftWidth,
                    bottomHeight,
                    rightWidth,
                    topButtonHeight,
                    leftButtonWidth,
                    bottomButtonHeight,
                    rightButtonWidth,
                    _topToolbars,
                    _bottomToolbars);

                topHeight = dimensions[0];
                leftWidth = dimensions[1];
                bottomHeight = dimensions[2];
                rightWidth = dimensions[3];

                //{{{ Lay out docking areas
                top.setBounds(
                    leftButtonWidth + leftWidth,
                    topButtonHeight,
                    _width - leftWidth - rightWidth,
                    topHeight);

                bottom.setBounds(
                    leftButtonWidth + leftWidth,
                    size.height - bottomHeight - bottomButtonHeight,
                    _width - leftWidth - rightWidth,
                    bottomHeight);

                left.setBounds(
                    leftButtonWidth,
                    0,
                    leftWidth,
                    _height);

                right.setBounds(
                    size.width - rightWidth - rightButtonWidth,
                    0,
                    rightWidth,
                    _height); //}}}
            }

            //{{{ Position tool bars if they are managed by us
            if(topToolbars != null)
            {
                topToolbars.setBounds(
                    leftButtonWidth + leftWidth,
                    topButtonHeight + topHeight,
                    size.width - leftWidth - rightWidth
                    - leftButtonWidth - rightButtonWidth,
                    _topToolbars.height);
            }

            if(bottomToolbars != null)
            {
                bottomToolbars.setBounds(
                    leftButtonWidth + leftWidth,
                    size.height - bottomHeight
                    - bottomButtonHeight
                    - _bottomToolbars.height
                    + topButtonHeight
                    + topHeight,
                    size.width - leftWidth - rightWidth
                    - leftButtonWidth - rightButtonWidth,
                    _bottomToolbars.height);
            } //}}}

            //{{{ Position center (edit pane, or split pane)
            if(center != null)
            {
                center.setBounds(
                    leftButtonWidth + leftWidth,
                    topButtonHeight + topHeight
                    + _topToolbars.height,
                    size.width
                    - leftWidth
                    - rightWidth
                    - leftButtonWidth
                    - rightButtonWidth,
                    size.height
                    - topHeight
                    - topButtonHeight
                    - bottomHeight
                    - bottomButtonHeight
                    - _topToolbars.height
                    - _bottomToolbars.height);
            } //}}}
        } //}}}

///===///

//{{{ insertUpdate() method
        public void insertUpdate(DocumentEvent evt)
        {
            // on insert, start search from beginning of
            // current match. This will continue to highlight
            // the current match until another match is found
            if(!hyperSearch.isSelected())
            {
                int start;
                JEditTextArea textArea = view.getTextArea();
                Selection s = textArea.getSelectionAtOffset(
                    textArea.getCaretPosition());
                if(s == null)
                    start = textArea.getCaretPosition();
                else
                    start = s.getStart();

                timerIncrementalSearch(start,false);
            }
        } //}}}

///===///

public void visitJumpInsn (final int opcode, final Label label) {
    if (CHECK) {
      if (label.owner == null) {
        label.owner = this;
      } else if (label.owner != this) {
        throw new IllegalArgumentException();
      }
    }
    if (computeMaxs) {
      if (opcode == Constants.GOTO) {
        // no stack change, but end of current block (with one new successor)
        if (currentBlock != null) {
          currentBlock.maxStackSize = maxStackSize;
          addSuccessor(stackSize, label);
          currentBlock = null;
        }
      } else if (opcode == Constants.JSR) {
        if (currentBlock != null) {
          addSuccessor(stackSize + 1, label);
        }
      } else {
        // updates current stack size (max stack size unchanged because stack
        // size variation always negative in this case)
        stackSize += SIZE[opcode];
        if (currentBlock != null) {
          addSuccessor(stackSize, label);
        }
      }
    }
    // adds the instruction to the bytecode of the method
    if (label.resolved && label.position - code.length < Short.MIN_VALUE) {
      // case of a backward jump with an offset < -32768. In this case we
      // automatically replace GOTO with GOTO_W, JSR with JSR_W and IFxxx <l>
      // with IFNOTxxx <l'> GOTO_W <l>, where IFNOTxxx is the "opposite" opcode
      // of IFxxx (i.e., IFNE for IFEQ) and where <l'> designates the
      // instruction just after the GOTO_W.
      if (opcode == Constants.GOTO) {
        code.put1(200); // GOTO_W
      } else if (opcode == Constants.JSR) {
        code.put1(201); // JSR_W
      } else {
        code.put1(opcode <= 166 ? ((opcode + 1) ^ 1) - 1 : opcode ^ 1);
        code.put2(8);   // jump offset
        code.put1(200); // GOTO_W
      }
      label.put(this, code, code.length - 1, true);
    } else {
      // case of a backward jump with an offset >= -32768, or of a forward jump
      // with, of course, an unknown offset. In these cases we store the offset
      // in 2 bytes (which will be increased in resizeInstructions, if needed).
      code.put1(opcode);
      label.put(this, code, code.length - 1, false);
    }
  }

///===///

    @Deprecated
    public Object buildTaggedValue(String tag, String value) {
        // TODO: Auto-generated method stub
        return null;
    }

///===///

/**
     * Detect a change event in MDR and convert this to a change event from the
     * model interface.  We also keep track of the number of pending changes so
     * that we can implement a simple flush interface.<p>
     *
     * The conversions are according to this table.
     * <pre>
     * MDR Event         MDR Event Type            Propogated Event
     *
     * InstanceEvent     EVENT_INSTANCE_DELETE     DeleteInstanceEvent
     * AttributeEvent    EVENT_ATTRIBUTE_SET       AttributeChangeEvent
     * AssociationEvent  EVENT_ASSOCIATION_ADD     AddAssociationEvent
     * AssociationEvent  EVENT_ASSOCIATION_REMOVE  RemoveAssociationEvent
     * </pre>
     * Any other events are ignored and not propogated beyond the model
     * subsystem.
     *
     * @param mdrEvent Change event from MDR
     * @see org.netbeans.api.mdr.events.MDRChangeListener#change
     */
    public void change(MDRChangeEvent mdrEvent) {
        
        if (eventThread == null) {
            eventThread = Thread.currentThread();
        }
        
        // TODO: This should be done after all events are delivered, but leave
        // it here for now to avoid last minute synchronization problems
        decrementEvents();

        // Quick exit if it's a transaction event
        // (we get a lot of them and they are all ignored)
        if (mdrEvent instanceof TransactionEvent) {
            return;
        }

        List<UmlChangeEvent> events = new ArrayList<UmlChangeEvent>();

        if (mdrEvent instanceof AttributeEvent) {
            AttributeEvent ae = (AttributeEvent) mdrEvent;
            events.add(new AttributeChangeEvent(ae.getSource(),
                    ae.getAttributeName(), ae.getOldElement(),
                    ae.getNewElement(), mdrEvent));
        } else if (mdrEvent instanceof InstanceEvent
                && mdrEvent.isOfType(InstanceEvent.EVENT_INSTANCE_DELETE)) {
            InstanceEvent ie = (InstanceEvent) mdrEvent;
            events.add(new DeleteInstanceEvent(ie.getSource(),
                    "remove", null, null, mdrEvent));
            // Clean up index entries
            String mofid = ((InstanceEvent)mdrEvent).getInstance().refMofId();
            modelImpl.removeElement(mofid);
        } else if (mdrEvent instanceof AssociationEvent) {
            AssociationEvent ae = (AssociationEvent) mdrEvent;
            if (ae.isOfType(AssociationEvent.EVENT_ASSOCIATION_ADD)) {
                events.add(new AddAssociationEvent(
                        ae.getNewElement(),
                        mapPropertyName(ae.getEndName()),
                        ae.getOldElement(), // will always be null
                        ae.getFixedElement(),
                        ae.getFixedElement(),
                        mdrEvent));
                // Create a change event for the corresponding property
                events.add(new AttributeChangeEvent(
                        ae.getNewElement(),
                        mapPropertyName(ae.getEndName()),
                        ae.getOldElement(), // will always be null
                        ae.getFixedElement(),
                        mdrEvent));
                // Create an event for the other end of the association
                events.add(new AddAssociationEvent(
                        ae.getFixedElement(),
                        otherAssocEnd(ae),
                        ae.getOldElement(), // will always be null
                        ae.getNewElement(),
                        ae.getNewElement(),
                        mdrEvent));
                // and a change event for that end
                events.add(new AttributeChangeEvent(
                        ae.getFixedElement(),
                        otherAssocEnd(ae),
                        ae.getOldElement(), // will always be null
                        ae.getNewElement(),
                        mdrEvent));
            } else if (ae.isOfType(AssociationEvent.EVENT_ASSOCIATION_REMOVE)) {
                events.add(new RemoveAssociationEvent(
                        ae.getOldElement(),
                        mapPropertyName(ae.getEndName()),
                        ae.getFixedElement(),
                        ae.getNewElement(), // will always be null
                        ae.getFixedElement(),
                        mdrEvent));
                // Create a change event for the associated property
                events.add(new AttributeChangeEvent(
                        ae.getOldElement(),
                        mapPropertyName(ae.getEndName()),
                        ae.getFixedElement(),
                        ae.getNewElement(), // will always be null
                        mdrEvent));
                // Create an event for the other end of the association
                events.add(new RemoveAssociationEvent(
                        ae.getFixedElement(),
                        otherAssocEnd(ae),
                        ae.getOldElement(),
                        ae.getNewElement(), // will always be null
                        ae.getOldElement(),
                        mdrEvent));
                // Create a change event for the associated property
                events.add(new AttributeChangeEvent(
                        ae.getFixedElement(),
                        otherAssocEnd(ae),
                        ae.getOldElement(),
                        ae.getNewElement(), // will always be null
                        mdrEvent));
            } else if (ae.isOfType(AssociationEvent.EVENT_ASSOCIATION_SET)) {
                LOG.error("Unexpected EVENT_ASSOCIATION_SET received");
            } else {
                LOG.error("Unknown association event type " + ae.getType());
            }
        } else {
            if (LOG.isDebugEnabled()) {
                String name = mdrEvent.getClass().getName();
                // Cut down on debugging noise
                if (!name.endsWith("CreateInstanceEvent")) {
                    LOG.debug("Ignoring MDR event " + mdrEvent);
                }
            }
        }

        for (UmlChangeEvent event : events) {
            fire(event);
            // Unregister deleted instances after all events have been delivered
            if (event instanceof DeleteInstanceEvent) {
                elements.unregister(null, ((RefBaseObject) event.getSource())
                        .refMofId(), null);
            }
        }
    }

///===///

    private String generateImports(Object cls, String packagePath) {
        // If the model is built by the import of Java source code, then a
        // component named after the filename was created, which manages the
        // import statements for all included classes/interfaces. This
        // component is now searched for cls in order to extract the imports.
        Object ns = Model.getFacade().getNamespace(cls);
        if (ns != null) {
            for (Object oe : Model.getFacade().getOwnedElements(ns)) {
                if (Model.getFacade().getUmlVersion().charAt(0) == '1' &&
                        Model.getFacade().isAComponent(oe)) {
                    for (Object re
                        : Model.getFacade().getResidentElements(oe)) {
                        Object r = Model.getFacade().getResident(re);
                        if (r.equals(cls)) {
                            return generateArtifactImports(oe);
                        }
                    }
                } else if (Model.getFacade().isAArtifact(oe)) {
                    if (Model.getCoreHelper().getUtilizedElements(oe)
                            .contains(cls)) {
                        return generateArtifactImports(oe);
                    }
                }
            }
        }
        // We now have the situation that no component with package imports
        // was found, so the import statements are guessed from the used model
        // elements inside cls.
        StringBuffer sb = new StringBuffer(80);
        HashSet<String> importSet = new java.util.HashSet<String>();

        // now check packages of all feature types
        for (Object mFeature : Model.getFacade().getFeatures(cls)) {
            if (Model.getFacade().isAAttribute(mFeature)) {
                String ftype = generateImportType(Model.getFacade().getType(
                        mFeature), packagePath);
                if (ftype != null) {
                    importSet.add(ftype);
                }
            } else if (Model.getFacade().isAOperation(mFeature)) {
                // check the parameter types
                for (Object parameter : Model.getFacade().getParameters(
                        mFeature)) {
                    String ftype = generateImportType(Model.getFacade()
                            .getType(parameter), packagePath);
                    if (ftype != null) {
                        importSet.add(ftype);
                    }
                }

                // check the return parameter types
                for (Object parameter 
                        : Model.getCoreHelper().getReturnParameters(mFeature)) {
                    String ftype = generateImportType(Model.getFacade()
                            .getType(parameter), packagePath);
                    if (ftype != null) {
                        importSet.add(ftype);
                    }
                }

                // check raised signals
                for (Object signal 
                        : Model.getFacade().getRaisedSignals(mFeature)) {
                    if (!Model.getFacade().isAException(signal)) {
                        continue;
                    }
                    String ftype = generateImportType(Model.getFacade()
                            .getType(signal), packagePath);
                    if (ftype != null) {
                        importSet.add(ftype);
                    }
                }
            }
        }

        // now check generalizations
        for (Object gen : Model.getFacade().getGeneralizations(cls)) {
            Object parent = Model.getFacade().getGeneral(gen);
            if (parent == cls) {
                continue;
            }

            String ftype = generateImportType(parent, packagePath);
            if (ftype != null) {
                importSet.add(ftype);
            }
        }

        // now check packages of the interfaces
        for (Object iface : Model.getFacade().getSpecifications(cls)) {
            String ftype = generateImportType(iface, packagePath);
            if (ftype != null) {
                importSet.add(ftype);
            }
        }

        // check association end types
        for (Object associationEnd : Model.getFacade().getAssociationEnds(cls))
        {
            Object association =
                Model.getFacade().getAssociation(associationEnd);
            for (Object associationEnd2 
                    : Model.getFacade().getConnections(association)) {
                if (associationEnd2 != associationEnd
                        && Model.getFacade().isNavigable(associationEnd2)
                        && !Model.getFacade().isAbstract(
                                Model.getFacade().getAssociation(
                                        associationEnd2))) {
                    // association end found
                    if (Model.getFacade().getUpper(associationEnd2) != 1) {
                        importSet.add("java.util.Vector");
                    } else {
                        String ftype =
                            generateImportType(Model.getFacade().getType(
                                    associationEnd2),
                                    packagePath);
                        if (ftype != null) {
                            importSet.add(ftype);
                        }
                    }
                }
            }

        }
        // finally generate the import statements
        for (String importType : importSet) {
            sb.append("import ").append(importType).append(";");
        sb.append(LINE_SEPARATOR);
        }
        if (!importSet.isEmpty()) {
            sb.append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

///===///

   public void addSuccessor(Object handle, Object mess) {
        // TODO: Auto-generated method stub
        
    }

///===///

public void act(File f) throws IOException {
    // skip backup files. This is actually a workaround for the
    // cpp generator, which always creates backup files (it's a
    // bug).
    if (!f.isDirectory() && !f.getName().endsWith(".bak")) {
        // TODO: This is using the default platform character
        // encoding.  Specifying an encoding will produce more 
        // predictable results
        FileReader fr = new FileReader(f);
        BufferedReader bfr = new BufferedReader(fr);
        try { 
            StringBuffer result =
                new StringBuffer((int) f.length());
            String line = bfr.readLine();
            do {
                result.append(line);
                line = bfr.readLine();
                if (line != null) {
                    result.append('\n');
                }
            } while (line != null);
            ret.add(new SourceUnit(f.toString().substring(
                    prefix), result.toString()));
        } finally {
            bfr.close();
            fr.close();
        }
    }
}

///===///

  /** Discarded. */
  public void processingInstruction(java.lang.String name,
                    java.lang.String data)
    throws javax.xml.transform.TransformerException {
    // nop
  }

///===///

/**
     * Save registered ID in our object map.
     * 
     * @param systemId
     *            URL of XMI field
     * @param xmiId
     *            xmi.id string for current object
     * @param object
     *            referenced object
     */
    @Override
    public void register(final String systemId, final String xmiId, 
            final RefObject object) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Registering XMI ID '" + xmiId 
                    + "' in system ID '" + systemId 
                    + "' to object with MOF ID '" + object.refMofId() + "'");
        }

        if (topSystemId == null) {
            topSystemId = systemId;
            try {
                baseUri = new URI(
                        systemId.substring(0, systemId.lastIndexOf('/') + 1));
            } catch (URISyntaxException e) {
                LOG.warn("Bad URI syntax for base URI from XMI document "
                        + systemId, e);
                baseUri = null;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Top system ID set to " + topSystemId);
            }
        }

        String resolvedSystemId = systemId;
        if (profile && systemId.equals(topSystemId)) {
            resolvedSystemId = modelPublicId;
        } else if (reverseUrlMap.get(systemId) != null) {
            resolvedSystemId = reverseUrlMap.get(systemId);
        } else {
            LOG.debug("Unable to map systemId - " + systemId);
        }

        RefObject o = getReferenceInt(resolvedSystemId, xmiId);
        if (o == null) {
            if (mofidToXmiref.containsKey(object.refMofId())) {
                XmiReference ref = mofidToXmiref.get(object.refMofId());
                // For now just skip registering this and ignore the request, 
                // but the real issue is that MagicDraw serializes the same 
                // object in two different composition associations, first in
                // the referencing file and second in the referenced file
                LOG.debug("register called twice for the same object "
                        + "- ignoring second");
                LOG.debug(" - first reference = " + ref.getSystemId() + "#"
                        + ref.getXmiId());
                LOG.debug(" - 2nd reference   = " + systemId + "#" + xmiId);
                LOG.debug(" -   resolved system id   = " + resolvedSystemId );
            } else {
                registerInt(resolvedSystemId, xmiId, object);
                super.register(resolvedSystemId, xmiId, object);
            }
        } else {
            if (o.equals(object)) {
                // Object from a different file, register with superclass so it
                // can resolve all references
                super.register(resolvedSystemId, xmiId, object);            
            } else {
               LOG.error("Collision - multiple elements with same xmi.id : "
                        + xmiId);
                throw new IllegalStateException(
                        "Multiple elements with same xmi.id");
            }
        }
    }

///===///

    public void removeAction(Object handle, Object action) {
        // TODO: Auto-generated method stub
        
    }

///===///

/*
     * @see org.argouml.uml.generator.CodeGenerator#generateFileList(java.util.Collection, boolean)
     */
    public Collection generateFileList(Collection elements, boolean deps) {
        LOG.debug("generateFileList() called");
        // TODO: 'deps' is ignored here
        File tmpdir = null;
        try {
            tmpdir = TempFileUtils.createTempDir();
            for (Object element : elements) {
                generateFile(element, tmpdir.getName());
            }
            return TempFileUtils.readFileNames(tmpdir);
        } finally {
            if (tmpdir != null) {
                TempFileUtils.deleteDir(tmpdir);
            }
        }
    }

///===///

    public Stereotype buildStereotype(final String name, final Object namespace) {
        RunnableClass run = new RunnableClass() {
            public void run() {
                Stereotype stereo = createStereotype();
                stereo.setName(name);
                if (namespace instanceof Package) {
                    stereo.setPackage((Package) namespace);
                }
                getParams().add(stereo);
        }
        };
        ChangeCommand cmd = new ChangeCommand(
                modelImpl, run,
                "Build a stereotype");
        editingDomain.getCommandStack().execute(cmd);
//        cmd.setObjects(run.getParams().get(0));
        return (Stereotype) run.getParams().get(0);
    }

///===///

public Name parseDisplayName(String nameInputStr) {
        
        // Treat a null name input string the same as an empty input string
        if (nameInputStr == null)
            nameInputStr = "";
        
        String displayName = nameInputStr;
        String salutation = null;
        String firstName = null;
        String middleName = null;
        String lastName = null;
        String title = null;
        
        // Get the salutation if one is specified
        salutation = findSalutation(nameInputStr);
        if (salutation != null) {
            // Remove the salutation
            nameInputStr = nameInputStr.substring(salutation.length());
        }
        
        // Get the title if one is specified
        title = findTitle(nameInputStr);
        if (title != null) {
            // Remove the title and the preceding comma
            nameInputStr = nameInputStr.substring(0, nameInputStr.length() - title.length());
            nameInputStr = nameInputStr.substring(0, nameInputStr.lastIndexOf(','));
        }
        
        // Determine whether there are 1, 2, or 3 names specified. These names should be separated by spaces
        //  or commas. If a comma separates the first two names, assume that the last name is specified first, 
        //  Otherwise, assume the first name is specified first. Middle name is always specified after the first name.
        StringTokenizer st = new StringTokenizer(nameInputStr, ", ");
        List<String> tokens = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            tokens.add(st.nextToken());
        }

        boolean commaSpecified = nameInputStr.indexOf(',') >= 0;
        if (tokens.size() == 0) {
            // Do nothing
        } else if (tokens.size() == 1) {
            // Assume last name only
            lastName = tokens.get(0);
        } else if (tokens.size() == 2) {
            if (commaSpecified) {
                lastName = tokens.get(0);
                firstName = tokens.get(1);
            } else {
                firstName = tokens.get(0);
                lastName = tokens.get(1);
            }
        } else if (tokens.size() == 3) {
            if (commaSpecified) {
                lastName = tokens.get(0);
                firstName = tokens.get(1);
                middleName = tokens.get(2);
            } else {
                firstName = tokens.get(0);
                middleName = tokens.get(1);
                lastName = tokens.get(2);
            }
        } else {
            // More than 3 tokens. Assume the last token is the last name and take the rest of the names as the first name.
            // This handles names like this: "Sue & Gene Stark".
            StringBuffer firstSB = new StringBuffer();
            lastName = (String)tokens.get(tokens.size()-1);
            firstName = nameInputStr.substring(0, nameInputStr.length() - lastName.length()).trim();
        }
        
        return new Name(displayName, salutation, firstName, middleName, lastName, title);
    }

///===///

/**
 * Inserts space between newlines if necessary to avoid
 * empty lines. Also inserts a space at the beginning if
 * the text starts with a newline.
 * @param t                Text to validate
 * @return                Validated text
 */
    private String validate(String t) {
        if ((t.indexOf("\n\n") == -1) && (!t.startsWith("\n"))) {
            return t;
        }

        StringBuffer result = new StringBuffer();

        // ensure that the text does not start with a newline
        if (t.startsWith("\n")) {
            result.append(' ');
        }

        // insert space btw. double newlines
        char last = ' ';

        for (int i = 0; i < t.length(); i++) {
            if ((t.charAt(i) == '\n') && (last == '\n')) {
                result.append(" \n");
            } else {
                result.append(t.charAt(i));
            }

            last = t.charAt(i);
        }

        return result.toString();
    }

///===///

// Load a file. This is what starts things off.

    /**
     * Loads from the InputStream into the root Xml Element.
     * 
     * @param input
     *            the input stream to load from.
     */
    public boolean load(InputStream input) {

        rootElement = new XmlElement(ROOT_XML_ELEMENT_NAME);
        currentElement = rootElement;

        try {
            // Create the XML reader...
            // xr = XMLReaderFactory.createXMLReader();
            SAXParserFactory factory = SAXParserFactory.newInstance();

            // Set the ContentHandler...
            // xr.setContentHandler( this );
            SAXParser saxParser = factory.newSAXParser();

            saxParser.parse(input, this);
        } catch (javax.xml.parsers.ParserConfigurationException ex) {
            LOG
                    .severe("XML config error while attempting to read from the input stream \n'"
                            + input + "'");
            LOG.severe(ex.toString());
            ex.printStackTrace();

            return (false);
        } catch (SAXException ex) {
            // Error
            LOG
                    .severe("XML parse error while attempting to read from the input stream \n'"
                            + input + "'");
            LOG.severe(ex.toString());
            ex.printStackTrace();

            return (false);
        } catch (IOException ex) {
            LOG
                    .severe("I/O error while attempting to read from the input stream \n'"
                            + input + "'");
            LOG.severe(ex.toString());
            ex.printStackTrace();

            return (false);
        }

        // XmlElement.printNode( getRoot(), "");
        return (true);
    }

///===///

/**
     * @see org.columba.api.command.Command#execute(Worker)
     */
    public void execute(IWorkerStatusController worker) throws Exception {
        POP3CommandReference r = (POP3CommandReference) getReference();

        server = r.getServer();

        // register interest on status bar information
        ((StatusObservableImpl) server.getObservable()).setWorker(worker);

        log(MailResourceLoader.getString("statusbar", "message",
                "authenticating"));

        try {
            // login and get # of messages on server
            totalMessageCount = server.getMessageCount();

            if (worker.cancelled())
                throw new CommandCancelledException();

            // synchronize local UID list with server UID list
            List newMessagesUidList = synchronize();

            if (worker.cancelled())
                throw new CommandCancelledException();

            if (Logging.DEBUG) {
                LOG.fine(newMessagesUidList.toString());
            }

            if (worker.cancelled())
                throw new CommandCancelledException();
            // only download new messages
            downloadNewMessages(newMessagesUidList, worker);

            // Delete old message from server if the feature is enabled
            server.cleanUpServer();

            // logout cleanly
            logout();

            // display downloaded message count in statusbar
            if (newMessageCount == 0) {
                log(MailResourceLoader.getString("statusbar", "message",
                        "no_new_messages"));
            } else {
                log(MessageFormat.format(MailResourceLoader.getString(
                        "statusbar", "message", "fetched_count"),
                        new Object[] { new Integer(newMessageCount) }));
            }
            
            // get inbox-folder from pop3-server preferences
            IMailbox inboxFolder = server.getFolder();
            
            // notify listeners
            IMailFolderCommandReference ref = new MailFolderCommandReference(inboxFolder, newMessagesUidList.toArray());
            MailCheckingManager.getInstance().fireNewMessageArrived(ref);
            
        } catch (CommandCancelledException e) {
            server.logout();

            // clear statusbar message
            server.getObservable().clearMessage();
        } catch (Exception e) {
            // clear statusbar message
            server.getObservable().clearMessage();
            throw e;
        }
        /*
         * catch (IOException e) { String name = e.getClass().getName();
         * JOptionPane.showMessageDialog(null, e.getLocalizedMessage(),
         * name.substring(name.lastIndexOf(".")), JOptionPane.ERROR_MESSAGE); //
         * clear statusbar message server.getObservable().clearMessage(); }
         */
        finally {
            /*
             * // always enable the menuitem again
             * r[0].getPOP3ServerController().enableActions(true);
             */
        }
    }

///===///

@Override
    public void execute(IWorkerStatusController worker) throws Exception {
        // get array of source references
        IMailFolderCommandReference r = (IMailFolderCommandReference) getReference();

        // does not work: r.getFolderName()
        // does not work: r.getMessage().getUID()

        // get source folder
        IMailbox srcFolder = (IMailbox) r.getSourceFolder();

        Hashtable<Object, IFolder> mails = new Hashtable<Object, IFolder>();

        // check if virtual folder, if yes, do not use these uids, use the
        // real uids instead
        if (srcFolder instanceof VirtualFolder) {
            // get original folder
            try {

                IHeaderList hl = ((IMailbox) r.getSourceFolder())
                        .getHeaderList();
                for (Object uid : r.getUids()) {
                    // should be virtual
                    mails.put(((VirtualHeader) hl.get(uid)).getSrcUid(),
                            ((VirtualHeader) hl.get(uid)).getSrcFolder());
                }
            } catch (Exception e) {
                LOG.severe("Error getting header list from virtual folder");
                e.printStackTrace();
            }
        } else {
            for (Object uid : r.getUids()) {
                mails.put(uid, r.getSourceFolder());
            }
        }

        if (type == ADD_TAG) {
            for (Entry<Object, IFolder> entry : mails.entrySet()) {
                AssociationStore.getInstance().addAssociation("tagging", id,
                        createURI(entry.getValue().getId(), entry.getKey()).toString());
            }
        } else if (type == REMOVE_TAG) {
            for (Entry<Object, IFolder> entry : mails.entrySet()) {
                AssociationStore.getInstance().removeAssociation("tagging", id,
                        createURI(entry.getValue().getId(), entry.getKey()).toString());
            }
        }
    }

///===///

    public void importMailboxFile(File file, IWorkerStatusController worker,
            IMailbox destFolder) throws Exception {
        int count = 0;
        boolean sucess = false;

        StringBuffer strbuf = new StringBuffer();

        BufferedReader in = new BufferedReader(new FileReader(file));
        String str;

        // parse line by line
        while ((str = in.readLine()) != null) {
            // if user cancelled task exit immediately
            if (worker.cancelled()) {
                return;
            }

            // if line doesn't start with "From" or line length is 0
            //  -> save everything in StringBuffer
            if (!str.startsWith("From ") || str.length() == 0) {
                strbuf.append(str + "\n");
            } else {
                // line contains "-" (mozilla mbox style)
                //  -> import message in Columba
                if (str.indexOf("-") != -1) {
                    if (strbuf.length() != 0) {
                        // found new message
                        saveMessage(strbuf.toString(), worker,
                            getDestinationFolder());

                        count++;

                        sucess = true;
                    }

                    strbuf = new StringBuffer();
                } else {
                    strbuf.append(str + "\n");
                }
            }
        }

        // save last message, because while loop aborted before being able to
        // save message
        if (sucess && strbuf.length() > 0) {
            saveMessage(strbuf.toString(), worker, getDestinationFolder());
        }

        in.close();
    }

///===///

public List<ISearchResult> query(String searchTerm,
            String searchCriteriaTechnicalName, boolean searchInside,
            int startIndex, int resultCount) {

        LOG.info("searchTerm=" + searchTerm);
        LOG.info("criteriaName=" + searchCriteriaTechnicalName);
        LOG.info("searchInside=" + searchInside);

        List<ISearchResult> result = new Vector<ISearchResult>();

        indizes = new Vector<SearchIndex>();

        // create search criteria

        FilterCriteria criteria = createFilterCriteria(searchTerm,
                searchCriteriaTechnicalName);

        // remember request id for "search in results"
        String searchRequestId = searchCriteriaTechnicalName;

        // remove memorized search folders
        if (!searchInside) {
            lastSearchFolder = null;
            //searchFolders.remove(searchRequestId);
        }

        // return empty result, in case the criteria doesn't match the search
        // term
        if (criteria == null)
            return result;
        try {

//          Iterator<String> it3 = searchFolders.keySet().iterator();
//          while (it3.hasNext()) {
//              String key = it3.next();
//              VirtualFolder f = searchFolders.get(key);
//              LOG.info("current cache id=" + key + ":" + f.getId());
//          }

            VirtualFolder folder = null;

            // create virtual folder for criteria
            if (searchInside) {
                if (lastSearchFolder != null) {
                    LOG.info("reuse existing virtual folder");

                    // get first one
                    VirtualFolder vFolder = lastSearchFolder;
                    // create new search folder, but re-use old search folder
                    folder = SearchFolderFactory.prepareSearchFolder(criteria,
                            vFolder);
                } else {
                    totalResultCount = 0;
                    return result;
                }
            } else {
                LOG.info("create new virtual folder");
                IMailFolder rootFolder = (IMailFolder) FolderTreeModel
                        .getInstance().getRoot();
                folder = SearchFolderFactory.createSearchFolder(criteria,
                        rootFolder);
            }

            // do the search
            IHeaderList headerList = folder.getHeaderList();

            Object[] uids = headerList.getUids();
            LOG.info("result count=" + uids.length);

            for (int i = 0; i < uids.length; i++) {
                SearchIndex idx = new SearchIndex(folder, uids[i]);

//              System.out.println("--> idx.folder="+idx.folder.getId());
//              System.out.println("--> idx.message="+idx.messageId);

                indizes.add(idx);
            }

            // retrieve the actual search result data
            List<ISearchResult> l = retrieveResultData(indizes, startIndex,
                    resultCount);
            result.addAll(l);

            // sort all the results
            Collections.sort(result, new MyComparator());

            // remember search folder for "show total results" action
            searchFolders.put(searchRequestId, folder);
            lastSearchFolder = folder;

            LOG.info("cache search folder=" + searchRequestId);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // memorize total result count
        totalResultCount = indizes.size();

        singleCriteriaSearch = true;

        return result;
    }

///===///

// create new stacked box
    private void createSearchStackedBox(String searchTerm, String providerName,
            String criteriaName) {
        if (searchTerm == null)
            throw new IllegalArgumentException("searchTerm == null");

        //remove(topPanel);
        removeAll();
        add(topPanel, BorderLayout.NORTH);
        add(searchBox, BorderLayout.CENTER);

        searchBox.removeAll();

        // search across all providers
        boolean providerAll = (providerName == null) ? true : false;
        // search all criteria in specific provider only
        boolean providerSearch = (providerName != null) ? true : false;
        // search in specific criteria
        boolean criteriaSearch = (criteriaName != null && providerName != null) ? true
                : false;

        ISearchManager manager = searchManager;

        if (criteriaSearch) {
            // query with only a single criteria

            ISearchProvider p = manager.getProvider(providerName);

            ISearchCriteria c = p.getCriteria(criteriaName, searchTerm);

            createResultPanel(p, c);

        } else if (providerSearch) {

            // query only a single provider

            ISearchProvider p = manager.getProvider(providerName);

            Iterator<ISearchCriteria> it2 = p.getAllCriteria(searchTerm)
                    .iterator();
            while (it2.hasNext()) {
                ISearchCriteria c = it2.next();
                createResultPanel(p, c);
            }

        } else if (providerAll) {
            // query all criteria of all providers

            Iterator<ISearchProvider> it = manager.getAllProviders();
            while (it.hasNext()) {
                ISearchProvider p = it.next();
                if (p == null)
                    continue;

                Iterator<ISearchCriteria> it2 = p.getAllCriteria(searchTerm)
                        .iterator();
                while (it2.hasNext()) {
                    ISearchCriteria c = it2.next();
                    createResultPanel(p, c);
                }
            }
        }

        // repaint box
        validate();
        repaint();
    }

///===///

    public void actionPerformed(ActionEvent e) {
        String action = e.getActionCommand();
        if (action.equals("OK")) {
            setVisible(false);
        } else if (action.equals("CANCEL")) {
            System.exit(0);
        } else if (action.equals("ADD")) {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            // bug #996381 (fdietz), directories only!!
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setFileHidingEnabled(false);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File location = fc.getSelectedFile();
                Profile p = new Profile(location.getName(), location);
                // add profile to profiles.xml
                ProfileManager.getInstance().addProfile(p);
                
                // add to listmodel
                model.addElement(p.getName());
                // select new item
                list.setSelectedValue(p.getName(), true);
            }
        } else if (action.equals("EDIT")) {
            String inputValue = JOptionPane.showInputDialog(
                GlobalResourceLoader.getString(RESOURCE_PATH, "profiles",
                "enter_name"), selection);
            
            if (inputValue == null) {
                return;
            }
            
            // rename profile in profiles.xml
            ProfileManager.getInstance().renameProfile(selection, inputValue);
            
            // modify listmodel
            model.setElementAt(inputValue, model.indexOf(selection));
            selection = inputValue;
        }
    }

///===///

    /*
 * Test for int hashCode().
 */
    public void testHashCode() {
        // first account item
        XmlElement xml = new XmlElement("account");
        xml.addAttribute("name", "my account");
        xml.addAttribute("uid", "0");

        XmlElement child = xml.addSubElement("identity");
        child.addAttribute("name", "John Doe");
        child.addAttribute("attach_signature", "false");
        child = xml.addSubElement("popserver");
        child.addAttribute("port", "25");
        child.addAttribute("login_method", "USER");
        child = xml.addSubElement("specialfolders");
        child.addAttribute("inbox", "101");
        child.addAttribute("sent", "104");

        AccountItem item = new AccountItem(xml);

        // second account item
        XmlElement xml2 = new XmlElement("account");
        xml2.addAttribute("uid", "0");
        xml2.addAttribute("name", "my account");

        XmlElement child2 = xml2.addSubElement("identity");
        child2.addAttribute("attach_signature", "false");
        child2.addAttribute("name", "John Doe");
        child2 = xml2.addSubElement("popserver");
        child2.addAttribute("login_method", "USER");
        child2.addAttribute("port", "25");
        child2 = xml2.addSubElement("specialfolders");
        child2.addAttribute("sent", "104");
        child2.addAttribute("inbox", "101");

        AccountItem item2 = new AccountItem(xml2);

        // third item, a bit different from the first
        XmlElement xml3 = new XmlElement("account");
        xml3.addAttribute("name", "my account");
        xml3.addAttribute("uid", "0");

        XmlElement child3 = xml3.addSubElement("identity");
        child3.addAttribute("name", "Kalle Kamel");
        child3.addAttribute("attach_signature", "false");
        child3 = xml3.addSubElement("popserver");
        child3.addAttribute("port", "25");
        child3.addAttribute("login_method", "USER");
        child3 = xml3.addSubElement("specialfolders");
        child3.addAttribute("inbox", "101");
        child3.addAttribute("sent", "104");

        AccountItem item3 = new AccountItem(xml3);

        // should have the same hashcodes...
        assertTrue("The hashcodes of item and item2 are not the same",
            item.hashCode() == item2.hashCode());

        // expect a different hashcode from a newly created item...
        assertFalse("The hashcodes of item and a new object are the same",
            item.hashCode() == (new AccountItem(new XmlElement())).hashCode());

        // expect a different hashcode for item and item3
        assertFalse("The hashcodes of item and item3 are the same",
            item.hashCode() == item3.hashCode());
    }

///===///

/**
     * Test the equivalent of EJB3 LockModeType.WRITE
     * <p/>
     * From the spec:
     * <p/>
     * If transaction T1 calls lock(entity, LockModeType.WRITE) on a versioned object, the entity
     * manager must avoid the phenomena P1 and P2 (as with LockModeType.READ) and must also force
     * an update (increment) to the entity's version column. A forced version update may be performed immediately,
     * or may be deferred until a flush or commit. If an entity is removed before a deferred version
     * update was to have been applied, the forced version update is omitted, since the underlying database
     * row no longer exists.
     * <p/>
     * The persistence implementation is not required to support calling lock(entity, LockMode-Type.WRITE)
     * on a non-versioned object. When it cannot support a such lock call, it must throw the
     * PersistenceException. When supported, whether for versioned or non-versioned objects, LockMode-Type.WRITE
     * must always prevent the phenomena P1 and P2. For non-versioned objects, whether or
     * not LockModeType.WRITE has any additional behaviour is vendor-specific. Applications that call
     * lock(entity, LockModeType.WRITE) on non-versioned objects will not be portable.
     * <p/>
     * Due to the requirement that LockModeType.WRITE needs to force a version increment,
     * a new Hibernate LockMode was added to support this behavior: {@link org.hibernate.LockMode#FORCE}.
     */
    public void testLockModeTypeWrite() {
        if ( !readCommittedIsolationMaintained( "ejb3 lock tests" ) ) {
            return;
        }
        if ( getDialect().doesReadCommittedCauseWritersToBlockReaders() ) {
            reportSkip( "deadlock", "jpa write locking" );
            return;
        }
        final String initialName = "lock test";
        // set up some test data
        Session s1 = getSessions().openSession();
        Transaction t1 = s1.beginTransaction();
        Item item = new Item();
        item.setName( initialName );
        s1.save( item );
        MyEntity myEntity = new MyEntity();
        myEntity.setName( "Test" );
        s1.save( myEntity );
        t1.commit();
        s1.close();

        Long itemId = item.getId();
        long initialVersion = item.getVersion();

        s1 = getSessions().openSession();
        t1 = s1.beginTransaction();
        item = (Item) s1.get( Item.class, itemId );
        s1.lock( item, LockMode.FORCE );
        assertEquals( "no forced version increment", initialVersion + 1, item.getVersion() );

        myEntity = (MyEntity) s1.get( MyEntity.class, myEntity.getId() );
        s1.lock( myEntity, LockMode.FORCE );
        assertTrue( "LockMode.FORCE on a unversioned entity should degrade nicely to UPGRADE", true );

        s1.lock( item, LockMode.FORCE );
        assertEquals( "subsequent LockMode.FORCE did not no-op", initialVersion + 1, item.getVersion() );

        Session s2 = getSessions().openSession();
        Transaction t2 = s2.beginTransaction();
        Item item2 = (Item) s2.get( Item.class, itemId );
        assertEquals( "isolation not maintained", initialName, item2.getName() );

        item.setName( "updated-1" );
        s1.flush();
        // currently an unfortunate side effect...
        assertEquals( initialVersion + 2, item.getVersion() );

        t1.commit();
        s1.close();

        item2.setName( "updated" );
        try {
            t2.commit();
            fail( "optimisitc lock should have failed" );
        }
        catch (Throwable ignore) {
            // expected behavior
            t2.rollback();
        }
        finally {
            s2.close();
        }

        s1 = getSessions().openSession();
        t1 = s1.beginTransaction();
        s1.delete( item );
        s1.delete( myEntity );
        t1.commit();
        s1.close();
    }

///===///

/**
     * Cascade to the collection elements
     */
    private void cascadeCollectionElements(
            final Object child,
            final CollectionType collectionType,
            final CascadeStyle style,
            final Type elemType,
            final Object anything,
            final boolean isCascadeDeleteEnabled) throws HibernateException {
        // we can't cascade to non-embedded elements
        boolean embeddedElements = eventSource.getEntityMode()!=EntityMode.DOM4J ||
                ( (EntityType) collectionType.getElementType( eventSource.getFactory() ) ).isEmbeddedInXML();
        
        boolean reallyDoCascade = style.reallyDoCascade(action) && 
            embeddedElements && child!=CollectionType.UNFETCHED_COLLECTION;
        
        if ( reallyDoCascade ) {
            if ( log.isTraceEnabled() ) {
                log.trace( "cascade " + action + " for collection: " + collectionType.getRole() );
            }
            
            Iterator iter = action.getCascadableChildrenIterator(eventSource, collectionType, child);
            while ( iter.hasNext() ) {
                cascadeProperty(
                        iter.next(), 
                        elemType,
                        style, 
                        anything, 
                        isCascadeDeleteEnabled 
                    );
            }
            
            if ( log.isTraceEnabled() ) {
                log.trace( "done cascade " + action + " for collection: " + collectionType.getRole() );
            }
        }
        
        final boolean deleteOrphans = style.hasOrphanDelete() && 
                action.deleteOrphans() && 
                elemType.isEntityType() && 
                child instanceof PersistentCollection; //a newly instantiated collection can't have orphans
        
        if ( deleteOrphans ) { // handle orphaned entities!!
            if ( log.isTraceEnabled() ) {
                log.trace( "deleting orphans for collection: " + collectionType.getRole() );
            }
            
            // we can do the cast since orphan-delete does not apply to:
            // 1. newly instantiated collections
            // 2. arrays (we can't track orphans for detached arrays)
            final String entityName = collectionType.getAssociatedEntityName( eventSource.getFactory() );
            deleteOrphans( entityName, (PersistentCollection) child );
            
            if ( log.isTraceEnabled() ) {
                log.trace( "done deleting orphans for collection: " + collectionType.getRole() );
            }
        }
    }

///===///

private void verifyModifications(long aId) {
        Session s = openSession();
        s.beginTransaction();

        // retrieve the A object and check it
        A a = ( A ) s.get( A.class, new Long( aId ) );
        assertEquals( aId, a.getId() );
        assertEquals( "Anthony", a.getData() );
        assertNotNull( a.getG() );
        assertNotNull( a.getHs() );
        assertEquals( 1, a.getHs().size() );

        G gFromA = a.getG();
        H hFromA = ( H ) a.getHs().iterator().next();

        // check the G object
        assertEquals( "Giovanni", gFromA.getData() );
        assertSame( a, gFromA.getA() );
        assertNotNull( gFromA.getHs() );
        assertEquals( a.getHs(), gFromA.getHs() );
        assertSame( hFromA, gFromA.getHs().iterator().next() );

        // check the H object
        assertEquals( "Hellen", hFromA.getData() );
        assertSame( a, hFromA.getA() );
        assertNotNull( hFromA.getGs() );
        assertEquals( 1, hFromA.getGs().size() );
        assertSame( gFromA, hFromA.getGs().iterator().next() );

        s.getTransaction().commit();
        s.close();
    }

///===///

public ForeignKey createForeignKey(String keyName, List keyColumns, String referencedEntityName,
                                       List referencedColumns) {
        Object key = new ForeignKeyKey( keyColumns, referencedEntityName, referencedColumns );

        ForeignKey fk = (ForeignKey) foreignKeys.get( key );
        if ( fk == null ) {
            fk = new ForeignKey();
            if ( keyName != null ) {
                fk.setName( keyName );
            }
            else {
                fk.setName( "FK" + uniqueColumnString( keyColumns.iterator(), referencedEntityName ) );
                //TODO: add referencedClass to disambiguate to FKs on the same
                //      columns, pointing to different tables
            }
            fk.setTable( this );
            foreignKeys.put( key, fk );
            fk.setReferencedEntityName( referencedEntityName );
            fk.addColumns( keyColumns.iterator() );
            if ( referencedColumns != null ) {
                fk.addReferencedColumns( referencedColumns.iterator() );
            }
        }

        if ( keyName != null ) {
            fk.setName( keyName );
        }

        return fk;
    }

///===///

private String generateSequentialSelect(Loadable persister) {
        //if ( this==persister || !hasSequentialSelects ) return null;

        //note that this method could easily be moved up to BasicEntityPersister,
        //if we ever needed to reuse it from other subclasses
        
        //figure out which tables need to be fetched
        AbstractEntityPersister subclassPersister = (AbstractEntityPersister) persister;
        HashSet tableNumbers = new HashSet();
        String[] props = subclassPersister.getPropertyNames();
        String[] classes = subclassPersister.getPropertySubclassNames();
        for ( int i=0; i<props.length; i++ ) {
            int propTableNumber = getSubclassPropertyTableNumber( props[i], classes[i] );
            if ( isSubclassTableSequentialSelect(propTableNumber) && !isSubclassTableLazy(propTableNumber) ) {
                tableNumbers.add( new Integer(propTableNumber) );
            }
        }
        if ( tableNumbers.isEmpty() ) return null;
        
        //figure out which columns are needed
        ArrayList columnNumbers = new ArrayList();
        final int[] columnTableNumbers = getSubclassColumnTableNumberClosure();
        for ( int i=0; i<getSubclassColumnClosure().length; i++ ) {
            if ( tableNumbers.contains( new Integer( columnTableNumbers[i] ) ) ) {
                columnNumbers.add( new Integer(i) );
            }
        }
        
        //figure out which formulas are needed
        ArrayList formulaNumbers = new ArrayList();
        final int[] formulaTableNumbers = getSubclassColumnTableNumberClosure();
        for ( int i=0; i<getSubclassFormulaTemplateClosure().length; i++ ) {
            if ( tableNumbers.contains( new Integer( formulaTableNumbers[i] ) ) ) {
                formulaNumbers.add( new Integer(i) );
            }
        }
        
        //render the SQL
        return renderSelect( 
            ArrayHelper.toIntArray(tableNumbers),
            ArrayHelper.toIntArray(columnNumbers),
            ArrayHelper.toIntArray(formulaNumbers)
        );
    }

///===///

    /**
     * {@inheritDoc}
     */
    protected ProxyFactory buildProxyFactory(PersistentClass persistentClass, Getter idGetter, Setter idSetter) {
        // determine the id getter and setter methods from the proxy interface (if any)
        // determine all interfaces needed by the resulting proxy
        HashSet proxyInterfaces = new HashSet();
        proxyInterfaces.add( HibernateProxy.class );
        
        Class mappedClass = persistentClass.getMappedClass();
        Class proxyInterface = persistentClass.getProxyInterface();

        if ( proxyInterface!=null && !mappedClass.equals( proxyInterface ) ) {
            if ( !proxyInterface.isInterface() ) {
                throw new MappingException(
                        "proxy must be either an interface, or the class itself: " + 
                        getEntityName()
                    );
            }
            proxyInterfaces.add( proxyInterface );
        }

        if ( mappedClass.isInterface() ) {
            proxyInterfaces.add( mappedClass );
        }

        Iterator iter = persistentClass.getSubclassIterator();
        while ( iter.hasNext() ) {
            Subclass subclass = ( Subclass ) iter.next();
            Class subclassProxy = subclass.getProxyInterface();
            Class subclassClass = subclass.getMappedClass();
            if ( subclassProxy!=null && !subclassClass.equals( subclassProxy ) ) {
                if ( !proxyInterface.isInterface() ) {
                    throw new MappingException(
                            "proxy must be either an interface, or the class itself: " + 
                            subclass.getEntityName()
                    );
                }
                proxyInterfaces.add( subclassProxy );
            }
        }

        Iterator properties = persistentClass.getPropertyIterator();
        Class clazz = persistentClass.getMappedClass();
        while ( properties.hasNext() ) {
            Property property = (Property) properties.next();
            Method method = property.getGetter(clazz).getMethod();
            if ( method != null && Modifier.isFinal( method.getModifiers() ) ) {
                log.error(
                        "Getters of lazy classes cannot be final: " + persistentClass.getEntityName() + 
                        "." + property.getName() 
                    );
            }
            method = property.getSetter(clazz).getMethod();
            if ( method != null && Modifier.isFinal( method.getModifiers() ) ) {
                log.error(
                        "Setters of lazy classes cannot be final: " + persistentClass.getEntityName() + 
                        "." + property.getName() 
                    );
            }
        }

        Method idGetterMethod = idGetter==null ? null : idGetter.getMethod();
        Method idSetterMethod = idSetter==null ? null : idSetter.getMethod();

        Method proxyGetIdentifierMethod = idGetterMethod==null || proxyInterface==null ? 
                null :
                ReflectHelper.getMethod(proxyInterface, idGetterMethod);
        Method proxySetIdentifierMethod = idSetterMethod==null || proxyInterface==null  ? 
                null :
                ReflectHelper.getMethod(proxyInterface, idSetterMethod);

        ProxyFactory pf = buildProxyFactoryInternal( persistentClass, idGetter, idSetter );
        try {
            pf.postInstantiate(
                    getEntityName(),
                    mappedClass,
                    proxyInterfaces,
                    proxyGetIdentifierMethod,
                    proxySetIdentifierMethod,
                    persistentClass.hasEmbeddedIdentifier() ?
                            (AbstractComponentType) persistentClass.getIdentifier().getType() :
                            null
            );
        }
        catch ( HibernateException he ) {
            log.warn( "could not create proxy factory for:" + getEntityName(), he );
            pf = null;
        }
        return pf;
    }

    protected ProxyFactory buildProxyFactoryInternal(PersistentClass persistentClass, Getter idGetter, Setter idSetter) {
        // TODO : YUCK!!!  fix after HHH-1907 is complete
        return Environment.getBytecodeProvider().getProxyFactoryFactory().buildProxyFactory();
//      return getFactory().getSettings().getBytecodeProvider().getProxyFactoryFactory().buildProxyFactory();
    }

///===///

FromElement createEntityJoin(
            String entityClass,
            String tableAlias,
            JoinSequence joinSequence,
            boolean fetchFlag,
            boolean inFrom,
            EntityType type) throws SemanticException {
        FromElement elem = createJoin( entityClass, tableAlias, joinSequence, type, false );
        elem.setFetch( fetchFlag );
        EntityPersister entityPersister = elem.getEntityPersister();
        int numberOfTables = entityPersister.getQuerySpaces().length;
        if ( numberOfTables > 1 && implied && !elem.useFromFragment() ) {
            if ( log.isDebugEnabled() ) {
                log.debug( "createEntityJoin() : Implied multi-table entity join" );
            }
            elem.setUseFromFragment( true );
        }

        // If this is an implied join in a FROM clause, then use ANSI-style joining, and set the
        // flag on the FromElement that indicates that it was implied in the FROM clause itself.
        if ( implied && inFrom ) {
            joinSequence.setUseThetaStyle( false );
            elem.setUseFromFragment( true );
            elem.setImpliedInFromClause( true );
        }
        if ( elem.getWalker().isSubQuery() ) {
            // two conditions where we need to transform this to a theta-join syntax:
            //      1) 'elem' is the "root from-element" in correlated subqueries
            //      2) The DotNode.useThetaStyleImplicitJoins has been set to true
            //          and 'elem' represents an implicit join
            if ( elem.getFromClause() != elem.getOrigin().getFromClause() ||
//                  ( implied && DotNode.useThetaStyleImplicitJoins ) ) {
                    DotNode.useThetaStyleImplicitJoins ) {
                // the "root from-element" in correlated subqueries do need this piece
                elem.setType( FROM_FRAGMENT );
                joinSequence.setUseThetaStyle( true );
                elem.setUseFromFragment( false );
            }
        }

        return elem;
    }

///===///

/**
     * Try to initialize a collection from the cache
     *
     * @param id The id of the collection of initialize
     * @param persister The collection persister
     * @param collection The collection to initialize
     * @param source The originating session
     * @return true if we were able to initialize the collection from the cache;
     * false otherwise.
     */
    private boolean initializeCollectionFromCache(
            Serializable id,
            CollectionPersister persister,
            PersistentCollection collection,
            SessionImplementor source) {

        if ( !source.getEnabledFilters().isEmpty() && persister.isAffectedByEnabledFilters( source ) ) {
            log.trace( "disregarding cached version (if any) of collection due to enabled filters ");
            return false;
        }

        final boolean useCache = persister.hasCache() && 
                source.getCacheMode().isGetEnabled();

        if ( !useCache ) {
            return false;
        }
        else {
            
            final SessionFactoryImplementor factory = source.getFactory();

            final CacheKey ck = new CacheKey( 
                    id, 
                    persister.getKeyType(), 
                    persister.getRole(), 
                    source.getEntityMode(), 
                    source.getFactory() 
                );
            Object ce = persister.getCacheAccessStrategy().get( ck, source.getTimestamp() );
            
            if ( factory.getStatistics().isStatisticsEnabled() ) {
                if ( ce == null ) {
                    factory.getStatisticsImplementor().secondLevelCacheMiss(
                            persister.getCacheAccessStrategy().getRegion().getName()
                    );
                }
                else {
                    factory.getStatisticsImplementor().secondLevelCacheHit(
                            persister.getCacheAccessStrategy().getRegion().getName()
                    );
                }

                
            }
            
            if (ce==null) {
                return false;
            }
            else {

                CollectionCacheEntry cacheEntry = (CollectionCacheEntry) persister.getCacheEntryStructure()
                        .destructure(ce, factory);
            
                final PersistenceContext persistenceContext = source.getPersistenceContext();
                cacheEntry.assemble(
                        collection, 
                        persister,  
                        persistenceContext.getCollectionOwner(id, persister)
                    );
                persistenceContext.getCollectionEntry(collection).postInitialize(collection);
                //addInitializedCollection(collection, persister, id);
                return true;
            }
            
        }
    }

///===///

private void evictOrRemoveTest(String configName) throws Exception {
    
        Configuration cfg = createConfiguration(configName);
        JBossCacheRegionFactory regionFactory = CacheTestUtil.startRegionFactory(cfg, getCacheTestSupport());
        Cache localCache = getJBossCache(regionFactory);
        boolean invalidation = CacheHelper.isClusteredInvalidation(localCache);
        
        // Sleep a bit to avoid concurrent FLUSH problem
        avoidConcurrentFlush();
        
        GeneralDataRegion localRegion = (GeneralDataRegion) createRegion(regionFactory, getStandardRegionName(REGION_PREFIX), cfg.getProperties(), null);
        
        cfg = createConfiguration(configName);
        regionFactory = CacheTestUtil.startRegionFactory(cfg, getCacheTestSupport());
        
        GeneralDataRegion remoteRegion = (GeneralDataRegion) createRegion(regionFactory, getStandardRegionName(REGION_PREFIX), cfg.getProperties(), null);
        
        assertNull("local is clean", localRegion.get(KEY));
        assertNull("remote is clean", remoteRegion.get(KEY));
        
        localRegion.put(KEY, VALUE1);
        assertEquals(VALUE1, localRegion.get(KEY));
        
        // allow async propagation
        sleep(250);
        Object expected = invalidation ? null : VALUE1;
        assertEquals(expected, remoteRegion.get(KEY));
        
        localRegion.evict(KEY);
        
        assertEquals(null, localRegion.get(KEY));
        
        assertEquals(null, remoteRegion.get(KEY));
    }

///===///

public void testQueryStatGathering() {
        Statistics stats = getSessions().getStatistics();
        stats.clear();

        Session s = openSession();
        Transaction tx = s.beginTransaction();
        fillDb(s);
        tx.commit();
        s.close();

        s = openSession();
        tx = s.beginTransaction();
        final String continents = "from Continent";
        int results = s.createQuery( continents ).list().size();
        QueryStatistics continentStats = stats.getQueryStatistics( continents );
        assertNotNull( "stats were null",  continentStats );
        assertEquals( "unexpected execution count", 1, continentStats.getExecutionCount() );
        assertEquals( "unexpected row count", results, continentStats.getExecutionRowCount() );
        long maxTime = continentStats.getExecutionMaxTime();
        assertEquals( maxTime, stats.getQueryExecutionMaxTime() );
//      assertEquals( continents, stats.getQueryExecutionMaxTimeQueryString() );

        Iterator itr = s.createQuery( continents ).iterate();
        // iterate() should increment the execution count
        assertEquals( "unexpected execution count", 2, continentStats.getExecutionCount() );
        // but should not effect the cumulative row count
        assertEquals( "unexpected row count", results, continentStats.getExecutionRowCount() );
        Hibernate.close( itr );

        ScrollableResults scrollableResults = s.createQuery( continents ).scroll();
        // same deal with scroll()...
        assertEquals( "unexpected execution count", 3, continentStats.getExecutionCount() );
        assertEquals( "unexpected row count", results, continentStats.getExecutionRowCount() );
        // scroll through data because SybaseASE15Dialect throws NullPointerException
        // if data is not read before closing the ResultSet
        while ( scrollableResults.next() ) {
            // do nothing
        }
        scrollableResults.close();
        tx.commit();
        s.close();

        // explicitly check that statistics for "split queries" get collected
        // under the original query
        stats.clear();
        s = openSession();
        tx = s.beginTransaction();
        final String localities = "from Locality";
        results = s.createQuery( localities ).list().size();
        QueryStatistics localityStats = stats.getQueryStatistics( localities );
        assertNotNull( "stats were null",  localityStats );
        // ...one for each split query
        assertEquals( "unexpected execution count", 2, localityStats.getExecutionCount() );
        assertEquals( "unexpected row count", results, localityStats.getExecutionRowCount() );
        maxTime = localityStats.getExecutionMaxTime();
        assertEquals( maxTime, stats.getQueryExecutionMaxTime() );
//      assertEquals( localities, stats.getQueryExecutionMaxTimeQueryString() );
        tx.commit();
        s.close();
        assertFalse( s.isOpen() );

        // native sql queries
        stats.clear();
        s = openSession();
        tx = s.beginTransaction();
        final String sql = "select id, name from Country";
        results = s.createSQLQuery( sql ).addEntity( Country.class ).list().size();
        QueryStatistics sqlStats = stats.getQueryStatistics( sql );
        assertNotNull( "sql stats were null", sqlStats );
        assertEquals( "unexpected execution count", 1, sqlStats.getExecutionCount() );
        assertEquals( "unexpected row count", results, sqlStats.getExecutionRowCount() );
        maxTime = sqlStats.getExecutionMaxTime();
        assertEquals( maxTime, stats.getQueryExecutionMaxTime() );
//      assertEquals( sql, stats.getQueryExecutionMaxTimeQueryString() );
        tx.commit();
        s.close();

        s = openSession();
        tx = s.beginTransaction();
        cleanDb( s );
        tx.commit();
        s.close();
    }













