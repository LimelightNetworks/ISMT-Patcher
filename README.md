README
------
This is a project based on JAAD an opensource java aac/mp4 decoder.  The 
tool that this project creates helps parse ISMT close-captioning files
for Microsoft Smooth Streaming and verifies that the tfhd box lengths
match the actual payload within them.  If the payload length doesn't match,
then the tfhd box length is updated.

The tool does not modify the original source file.  Instead it copies
the source file to an alternate name postfixed by .patch, and then
modifies that file.

PREREQUISITES
-------------
Java version 1.6 or higher.

USAGE
-----
java -jar ismt-patcher.jar filename.ismt

In this example, filename.ismt is the source file that needs to be verified
and fixed if necessary.  Prior to doing so, filename.ismt is copied over
to filename.ismt.patch, and then filename.ismt.patch is modified with the
correct offset data.

To use the resulting files, you will need to rename them to their original
names.

COMPILING
---------
An ant build.xml file is included. 

Run "ant create_patcher" to compile and generate the jar.

The resulting file is placed in ismt-patcher/bin/ismt-patcher.jar

