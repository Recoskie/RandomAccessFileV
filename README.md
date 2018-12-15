# RandomAccessFileV

Simulates a ram address space using very little ram Memory, and time on CPU.

This tool is very useful for mapping areas of an file at random positions and reading the data in order using Virtual read, or write operations.

This tool is also useful for mapping parts of an program into a simulated virtual space that is to be modified, or changed. Thus loading all the patches between. Allowing you to write directly to the patches on disk when making changes, and to the program.

# Detailed Description

Note that **RandomAccessFileV** is the same as **RandomAccessFile** except you have both a **file pointer**, and **virtual address pointer**.

The **virtual pointer** reads within the areas that are mapped in address space using **V** versions of **read/write/seek**.

Using method **addV( long FileOffset, long DataLen, long Address, long AddressLen )**. You select the positions in the file that are at the selected position. For methods **readV/writeV/seekV**.

Anything unmapped is read as **0x00 bytes** using **readV**, and can not be changed using using **writeV** unless it is an mapped byte from the file.

If the the unmapped **read**, or **write** methods are used than **file pointer** moves, and also the **Virtual pointer moves** as it is relative to the **file pointer**. This allows you to mix both mapped and unmapped IO operations.

All **RandomAcesssFile** methods are the original operations with no overrides. So there is no performance penalties.

The virtual operations use the original read/write, but seek to the next address in the map at file position by moving up one in index in the virtual address map. The **V** operations do basically **one additional compare**. Thus as read/write are called the **File pointer updates**, and is added relative to **Virtual pointer**.

**Additionally** as addresses are added. Any address that writes to the start of a address will move the start of the address in file position to number of bytes written over. Matching the position on disk to bytes written over in virtual space. Any address that writes to the end of an address will crop the end of the address -1 to the start of the added address. All addresses with less than 0 bytes will be removed. Further allowing patches and updates to be easily mapped as they are added. While making it easy to read, or write over the patches and sections accordingly to your desired modifications. Because of organization and the address removing system the **readV/writeV/seakV** operations remain blazing fast.

When using **seekV** anything less than the current virtual address will scan down in index, and anything further away in index will scan up one in index.

This is a very high performance IO system for mapping and reading fragmented positions in a straight line, and at selected virtual address locations. This system allows you to modify programs/files that are bigger than the installed ram memory in your device by reading, and writing directly to the file in an simulated address space.

# Methods.

1. Method **addV( long FileOffset, long DataLen, long Address, long AddressLen )**
    > ##### Add an address to Virtual address map.
2. Method **readV()**.
    > ##### read current byte at virtual address position.
    > ##### Returns byte as int.
3. Method **readV( byte[] b )**.
    > ##### read len bytes into byte array from current virtual address pointer.
4. Method **readV( byte[] b, int off, int len )**.
    > ##### Read select len bytes into byte array at select offset to write bytes into array b at the current virtual address pointer.
5. Method **writeV( int byte )**.
    > ##### Write a byte at the current virtual address pointer.
6. Method **writeV( byte[] b )**.
    > ##### Write the byte array at the current virtual address pointer.
7. Method **writeV( byte[] b, int off, int len )**.
    > ##### Write selected offset bytes from byte array to len at the current virtual address pointer.
8. Method **seekV( long Address )**.
    > ##### Write selected offset bytes from byte array to len at the current virtual address pointer.
9. Method **getVirtualPointer()**.
    > ##### Seek to a specific virtual address.
    > ##### Updates "file pointer", and "virtual pointer" relatively.
10. Method **resetV()**.
    > ##### Resets the virtual address map.

Thus all methods from Random access file are extended. See [Random access file documentation](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/io/RandomAccessFile.html) for the rest of the supported methods.
