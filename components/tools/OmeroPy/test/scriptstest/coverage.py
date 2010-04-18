#!/usr/bin/env python

"""
   Integration test demonstrating various script creation methods

   Copyright 2010 Glencoe Software, Inc. All rights reserved.
   Use is subject to license terms supplied in LICENSE.txt

"""

import integration.library as lib
import unittest, os, sys, uuid

import omero
import omero.rtypes as OR


class TestCoverage(lib.ITest):

    def setUp(self):
        lib.ITest.setUp(self)
        self.rs = self.root.sf.getScriptService()
        self.us = self.client.sf.getScriptService()

    def testGetScripts(self):
        """
        getScripts returns official scripts, several of which are shipped with OMERO.
        """
        self.assert_( len(self.rs.getScripts()) > 0 )
        self.assert_( len(self.us.getScripts()) > 0 )
        self.assert_( len(self.us.getUserScripts([])) == 0) # New user. No scripts

    def testGetScriptWithDetails(self):
        scriptList = self.us.getScripts()
        script = scriptList[0]
        scriptMap = self.us.getScriptWithDetails(script.id.val)

        self.assertEquals(1, len(scriptMap))
        scriptText = scriptMap.keys()[0]
        scriptObj = scriptMap.values()[0]

    def testUploadAndScript(self):
        scriptID = self.us.uploadScript("/OME/Foo.py", """if True:
        import omero
        import omero.grid as OG
        import omero.rtypes as OR
        import omero.scripts as OS
        client = OS.client("testUploadScript")
        print "done"
        """)
        return scriptID


    def testUserCantUploadOfficalScript(self):
        self.us.uploadOfficialScript("/fails.py", """if True:
        import omero
        """)


if __name__ == '__main__':
    unittest.main()
