package RandomAccessFileV;
import java.io.*;

//Sector read version.

public class RandomAccessDevice extends RandomAccessFileV
{
  //Sector buffer. Sector size must be known to read disk.

  private static byte[] sector;

  //Used to keep track of position in sectors. Allowing unaligned read.

  private long TempPos = 0;

  //Base address, of sector.

  private long base = 0;

  //Data start position within sector.

  private int pos = 0;

  //Start, and end sector.

  private int sectN = 0, sectE = 0;

  //Number of readable bytes. Can be smaller, Because reached end of disk, or bad sector.

  private int r = 0;

  //Used to restore the original event state at end of method.

  private boolean e = false;

  //The size of a raw disk volume. Does not really need to be calculated in order to read a disk.

  private long size = 0;

  //Path to disk.

  private String path = "";

  public RandomAccessDevice( File file, String mode ) throws FileNotFoundException
  {
    super( file, mode ); path = file.getPath();
    
    //Sector size.

    try { getSectorSize(); } catch( IOException e ) { throw( new FileNotFoundException("") ); }
  }
  
  public RandomAccessDevice( String name, String mode ) throws FileNotFoundException
  {
    super( name, mode ); path = name;

    //Sector size.

    try { getSectorSize(); } catch( IOException e ) { throw( new FileNotFoundException("") ); }
  }

  //Get sector size.

  private void getSectorSize() throws IOException
  {
    super.Events = false;

    boolean end = false; sectN = 1; while( !end && sectN <= 0x1000 ) { sector = new byte[ sectN ]; try { super.read( sector ); end = true; } catch( IOException e ) { sectN <<= 1; } }

    if( !end ) { throw( new IOException("Disk Error!") ); } else { super.seek(0); super.Events = true; }
  }

  @Override public void seekV( long pos ) throws IOException
  {
    this.seek( pos );
  }

  //Read data at any position in sectors as a regular Random access file.
  
  @Override public int readV() throws IOException
  {
    return( this.read() );
  }

  @Override public int read() throws IOException
  {
    //Disable event during operation.

    super.syncR(); e = super.Events; super.Events = false;

    //Read operation.

    TempPos = super.getFilePointer(); base = ( TempPos / sector.length ) * sector.length;

    super.seek( base ); super.read( sector ); super.seek( TempPos + 1 );
      
    r = (int)sector[(int)( TempPos - base )];

    //Restore event state.
    
    super.Events = e; return( r );
  }

  @Override public int readV( byte[] b ) throws IOException
  {
    return( this.read( b ) );
  }

  @Override public int read( byte[] b ) throws IOException
  {
    //Disable event during operation.

    super.syncR(); e = super.Events; super.Events = false;

    //Sector start address.

    TempPos = super.getFilePointer(); base = ( TempPos / sector.length ) * sector.length;

    //Start of position within sector.

    pos = (int)( TempPos - base );
    
    //Number of sectors needed, for the size of the data being read.
    
    sectE = (int)( ( ( pos + b.length ) / sector.length ) + 1 );

    //Start sector and number of read bytes inti 0.

    sectN = 0; r = 0;

    //Move to Sector start address.

    super.seek( base );

    //Read and test each sector.
    
    try
    {
      while( sectN < sectE )
      {
        super.read( sector );

        while( pos < sector.length && r < b.length ){ b[ r++ ] = sector[ pos++ ]; }
        
        pos = 0; sectN += 1;
      }
    } catch( IOException er ) {}

    //Add original file potion by read operation size.
    
    super.seek( TempPos + b.length );

    //Restore event state.

    super.Events = e; return( r );
  }

  @Override public int readV( byte[] b, int off, int len ) throws IOException
  {
    return( this.read( b, off, len ) );
  }
  
  @Override public int read( byte[] b, int off, int len ) throws IOException
  {
    //Disable event during operation.

    super.syncR(); e = super.Events; super.Events = false;

    //Sector start address.

    TempPos = super.getFilePointer(); base = ( TempPos / sector.length ) * sector.length;

    //Start of position within sector.

    pos = (int)( TempPos - base );
    
    //Number of sectors needed, for the size of the data being read.
    
    sectE = (int)( ( ( pos + b.length ) / sector.length ) + 1 );

    //Start sector and number of read bytes inti 0.

    sectN = 0; r = off;

    //Move to Sector start address.

    super.seek( base );

    //Read and test each sector.
    
    try
    {
      while( sectN < sectE )
      {
        super.read( sector );

        while( pos < sector.length && r < len ){ b[ r++ ] = sector[ pos++ ]; }
        
        pos = 0; sectN += 1;
      }
    } catch( IOException er ) {}

    //Add original file potion by read operation size.
    
    super.seek( TempPos + b.length );

    //Restore event state.

    super.Events = e; return( r - off );
  }

  //The size of the disk.

  @Override public long length()
  {
    //If the built in IO length method could not find size then size is 0.

    if( size == 0 )
    {
      super.Events = false; try { TempPos = super.getFilePointer(); } catch( IOException e ) { }

      try
      {
        //Windows reports disks a few sectors less than they should be.
        //This gets us as close as possible to the actual size without having to scan the disk fully.

        if( path.startsWith("\\\\.\\") )
        {
          String index = path.substring( 17, path.length() );

          Process p = new ProcessBuilder(new String[] { "cmd", "/c", "wmic diskdrive GET index,size /format:table" }).start();

          InputStream b = p.getInputStream(); String[] s = new String( b.readAllBytes() ).split("\r\r\n");

          int sep = s[0].indexOf("Size"); for( int i = 0; i < s.length; i++ )
          {
            if( s[i].startsWith(index) )
            {
              size = Long.parseUnsignedLong(s[i].substring( sep ).replaceAll(" ", ""), 10 ) + 5102 * sector.length;

              super.seek( size + sector.length ); super.read( sector ); //Goto exception if size is last sector.
            }
          }
        }

        //MacOS

        else if( path.startsWith( "/dev/rdisk" ) )
        {
          Process p = new ProcessBuilder(new String[] { "/bin/bash", "-c", "diskutil info " + path + " | grep \"Disk Size\"" }).start();

          String s = new BufferedReader( new InputStreamReader( p.getInputStream() ) ).readLine();

          size = Long.parseUnsignedLong( s.substring( s.indexOf("(") + 1, s.indexOf(" Bytes)") ), 10 );

          return( size );
        }
        
        //Linux
        
        else if( path.startsWith( "/dev/sd" ) )
        {
          Process p = new ProcessBuilder(new String[] { "/bin/bash", "-c", "lsblk -bno SIZE " + path }).start();

          size = Long.parseUnsignedLong( new BufferedReader( new InputStreamReader( p.getInputStream() ) ).readLine(), 10 );
          
          return( size );
        }
      }
      catch( IOException e )
      {
        //Determines the last sector was found in windows without any further calculation.

        if( size != 0 ) { try { super.seek( size ); super.read( sector ); size = size + sector.length - 1; return( size ); } catch( IOException er ) { } }
      }

      //Failed to query disk size information, then calculate the length of a raw disk volume (Slow and is done before OS boots).

      long bit = sector.length; boolean end = false;

      //Find the disk multiple of 2 size if size is 0.
    
      if( size == 0 ) { end = false; while( bit <= 0x4000000000000000L && !end ) { try { super.seek( bit <<= 1 ); super.read( sector ); } catch( IOException e ) { bit >>= 1; size |= bit; end = true; } } }
      
      //If size is not 0, then we queried the disk size in multiple of 2, but it may be a few sectors off.
      
      else { bit = 1L << ( (int)( Math.log( size ) / Math.log(2) ) ); size = bit; bit >>= 1; }

      //Calculate the final bit placements for the final sector.

      while( bit >= sector.length ) { try { super.seek( size | bit ); super.read( sector ); size |= bit; } catch( IOException e ) { } bit >>= 1; }

      //Add last sector -1 byte, for exact size in bytes.

      size += sector.length - 1;

      //Restore the state of the IO system.
    
      try { super.seek( TempPos ); } catch( IOException e ) { } super.Events = true;
    }

    //Return size.

    return( size );
  }
}