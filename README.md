# RandomAccessFileV

This is the web version of RandomAccessFileV. It uses the <a href="https://developer.mozilla.org/en-US/docs/Web/API/FileReader">FileReader API</a> built into javascript for reading files.

This version is different, but designed to do event handling based IO, and Virtual address mapping as well.

The script exists as part of RandomAccessFileV, but is called FileReaderV to signify the difference.

Simulates a ram address space using very little ram Memory, and time on CPU.

This tool is very useful for mapping areas of an file at random positions and reading the data in order using Virtual read.

This tool is also useful for mapping parts of an program into a simulated virtual space that is to be modified, or changed. Thus loading all the patches between. Allowing you to write directly to the patches on disk when making changes, and to the program.
