import java.io.*;
import java.util.*;
import javax.swing.event.*;

class IOEvent extends EventObject
{
  public int type = 0;
  public int length = 0;
  public int pos = 0;
  
  public IOEvent( Object source )
  {
    super( source );
  }
  
  public IOEvent( Object source, int t )
  {
    super( source ); type = t;
  }
}

interface IOEventListener extends EventListener
{
  public void onSeek( IOEvent evt );
  public void onRead( IOEvent evt );
  public void onWrite( IOEvent evt );
}

public class RandomAccessFileV extends RandomAccessFile
{
  //My event listener list for graphical components that listen for stream update events.
  
  protected EventListenerList list = new EventListenerList();
  
  //Disable events. This is to stop graphics components from updating while doing intensive operations.
  
  public boolean Events = true;

  //Add and remove event listeners.

  public void addMyEventListener( IOEventListener listener ) { list.add( IOEventListener.class, listener ); }
  public void removeMyEventListener( IOEventListener listener ) { list.remove( IOEventListener.class, listener ); }
  
  //Fire the event to all my graphics components for editing the stream or decoding data types.
  
  void fireIOEvent ( IOEvent evt )
  {
    Object[] listeners = list.getListenerList();
    
    if ( Events )
    {
      for ( int i = 0; i < listeners.length; i = i + 2 )
      {
        if ( listeners[i] == IOEventListener.class )
        {
          if( evt.type == 0 ) { ((IOEventListener)listeners[i+1]).onSeek( evt ); }
          else if( evt.type == 1 ) { ((IOEventListener)listeners[i+1]).onRead( evt ); }
          else if( evt.type == 2 ) { ((IOEventListener)listeners[i+1]).onWrite( evt ); }
        }
      }
    }
  }
  
  //64 bit address pointer. Used by things in virtual ram address space such as program instructions, and data.
  
  private long VAddress = 0x0000000000000000L;
  
  //Positions of an file can be mapped into ram address space locations.

  private class VRA
  {
    //General address map properties.
    
    private long Pos = 0x0000000000000000L, Len = 0x0000000000000000L, VPos = 0x0000000000000000L, VLen = 0x0000000000000000L;
    
    //File offset end position.
    
    private long FEnd = 0x0000000000000000L;
    
    //Virtual address end position. If grater than actual data the rest is 0 filled space.
    
    private long VEnd = 0x0000000000000000L;
    
    //Construct area map. Both long/int size. Note End position can match the start position as the same byte. End position is minus 1.
    
    public VRA( long Offset, long DataLen, long Address, long AddressLen )
    {
      Pos = Offset; Len = DataLen; VPos = Address; VLen = AddressLen;
      
      //Negative not allowed because of java's signified compare.
      
      if( Pos < 0 ) { Pos = 0; } if( VPos < 0 ) { VPos = 0; }
      if( Len < 0 ) { Len = 0; } if( VLen < 0 ) { VLen = 0; }
      
      //Data offset length can't be higher than virtual offset length.
      
      if( Len > VLen ){ Len = VLen; }
      
      //Calculate file offset end positions and virtual end positions.
      
      FEnd = Pos + ( Len - 1 ); VEnd = VPos + ( VLen - 1 );
    }
    
    //Set the end of an address when another address writes into this address.
    
    public void setEnd( long Address )
    {
      //Set end of the current address to the start of added address.
      
      VEnd = Address;
      
      //Calculate address length.
      
      VLen = ( VEnd + 1 ) - VPos;
      
      //If there still is data after the added address.
      
      Len = Math.min( Len, VLen ); 
      
      //Calculate the bytes written into.
      
      FEnd = Pos + ( Len - 1 );
    }
    
    //Addresses that write over the start of an address.
    
    public void setStart( long Address )
    {
      //Add Data offset to bytes written over at start of address.
        
      Pos += Address - VPos;
        
      //Move Virtual address start to end of address.
        
      VPos = Address;
        
      //Recalculate length between the new end position.
        
      Len = ( FEnd + 1 ) - Pos; VLen = ( VEnd + 1 ) - VPos;
    }
    
    //String Representation for address space.
    
    public String toString()
    {
      return( "File(Offset)=" + String.format( "%1$016X", Pos ) + "---FileEnd(Offset)=" + String.format( "%1$016X", FEnd ) + "---Start(Address)=" + String.format( "%1$016X", VPos ) + "---End(Address)=" + String.format( "%1$016X", VEnd ) + "---Length=" + VLen );
    }
  }
  
  //The mapped addresses.
  
  private java.util.ArrayList<VRA> Map = new java.util.ArrayList<VRA>();
  
  //The virtual address that the current virtual address pointer is in range of.
  
  private VRA curVra;
  
  //Speeds up search. By going up or down from current virtual address.
  
  private int Index = -1;
  
  //Map.size() is slower than storing the mapped address space size.
  
  private int MSize = 1;
  
  //Construct the reader using an file, or disk drive.
  
  public RandomAccessFileV( File file, String mode ) throws FileNotFoundException { super( file, mode ); Map.add( new VRA( 0, 0, 0, 0x7FFFFFFFFFFFFFFFL ) ); curVra = Map.get(0); }
  
  public RandomAccessFileV( String name, String mode ) throws FileNotFoundException { super( name, mode ); Map.add( new VRA( 0, 0, 0, 0x7FFFFFFFFFFFFFFFL ) ); curVra = Map.get(0); }
  
  //Temporary read only data.
  
  private static File TFile;
  
  private static File mkf() throws IOException { TFile = File.createTempFile("",".tmp"); TFile.deleteOnExit(); return( TFile ); }
  
  public RandomAccessFileV( byte[] data ) throws IOException
  {
    super( mkf(), "r" ); super.write( data );
    
    Map.add( new VRA( 0, data.length, 0, data.length ) ); curVra = Map.get(0);
    
    TFile.delete();
  }
  
  public RandomAccessFileV( byte[] data, long Address ) throws IOException
  {
    super( mkf(), "r" ); super.write( data );
    
    Map.add( new VRA( 0, (long)data.length, Address, (long)data.length ) ); curVra = Map.get(0);
    
    TFile.delete();
  }

  //Reset the Virtual ram map.
  
  public void resetV()
  {
    Map.clear();
    
    Map.add( new VRA( 0, 0, 0, 0x7FFFFFFFFFFFFFFFL ) );
    
    MSize = 1; Index = -1; VAddress = 0;
  }
  
  //Get the virtual address pointer. Relative to the File offset pointer.
  
  public long getVirtualPointer() throws IOException { return( super.getFilePointer() + VAddress ); }
  
  //Add an virtual address.
  
  public void addV( long Offset, long DataLen, long Address, long AddressLen ) 
  {
    VRA Add = new VRA( Offset, DataLen, Address, AddressLen );
    VRA Cmp = null;
    
    //The numerical range the address lines up to in index in the address map.
    
    int e = 0;
    
    //fixes lap over ERROR.
    
    boolean sw = true;
    
    //If grater than last address then add to end.
    
    if( MSize > 0 && Add.VPos > Map.get( MSize - 1 ).VEnd ){ Map.add( Add ); MSize++; return; }
    
    //Else add and write in alignment.
    
    for( int i = 0; i < MSize; i++ )
    {
      Cmp = Map.get( i );
      
      //If the added address writes to the end, or in the Middle of an address.
      
      if( Add.VPos <= Cmp.VEnd && Add.VPos > Cmp.VPos && sw )
      {
        //Address range position.
        
        e = i + 1;
        
        //If the added address does not write to the end of the address add it to the next element.
        
        if( Cmp.VEnd > Add.VEnd )
        {
          sw = false; Map.add( e, new VRA( Cmp.Pos, Cmp.Len, Cmp.VPos, Cmp.VLen ) ); MSize++;
        }
        
        //Set end of the current address to the start of added address.
        
        Cmp.setEnd( Add.VPos - 1 );
      }
      
      //If added Address writes to the start of Address.
      
      else if( Add.VPos <= Cmp.VPos && Cmp.VPos <= Add.VEnd || !sw )
      {        
        //Address range position.
        
        e = i;
        
        //Add Data offset to bytes written over at start of address.
        
        Cmp.setStart( Add.VEnd + 1 );
        
        //Remove overwritten addresses that are negative in length remaining.
        
        if( Cmp.VLen <= 0 ) { Map.remove( i ); i--; MSize--; }
        
        //Else if 0 or less data. Set data length and offset 0.
        
        else if( Cmp.Len <= 0 ){ Cmp.Pos = 0; Cmp.Len = 0; Cmp.FEnd = 0; }
        
        sw = true;
      }
    }
    
    //Add address in order to it's position in range.
    
    Map.add( e, Add ); MSize++;
    
    //If added address lines up with Virtual address pointer. Seek the new address position.
    
    try { if( VAddress >= Add.VPos && VAddress <= Add.VEnd ) { seekV( VAddress ); } } catch( IOException ex ) {  }
  }
  
  //fire seek event.
  
  public void seek( long Offset ) throws IOException
  {
    super.seek( Offset );
    fireIOEvent( new IOEvent( this, 0 ) );
  }
  
  //fire read event.
  
  public int read() throws IOException
  {
    fireIOEvent( new IOEvent( this, 1 ) );
    return( super.read() );
  }
  
  //Adjust the Virtual offset pointer relative to the mapped virtual ram address and file pointer.
  
  public void seekV( long Address ) throws IOException
  {
    //If address is in range of current address index.
    
    if( Address >= curVra.VPos && Address <= ( curVra.VPos + curVra.Len ) )
    {
      super.seek( ( Address - curVra.VPos ) + curVra.Pos );
      
      VAddress = Address - super.getFilePointer();
    }
    
    //If address is grater than the next vra iterate up in indexes.
    
    else if( Address >= curVra.VPos || Index == -1 )
    {
      VRA e = null;
      
      for( int n = Index + 1; n < MSize; n++ )
      {
        e = Map.get( n );
        
        if( Address >= e.VPos && Address <= ( curVra.VPos + curVra.Len ) )
        {
          Index = n; curVra = e;
          
          super.seek( ( Address - e.VPos ) + e.Pos );
          
          VAddress = Address - super.getFilePointer();
          
          return;
        }
      }
    }
    
    //else iterate down in indexes.
    
    else if( Address <= curVra.VPos )
    {
      VRA e = null;
      
      for( int n = Index - 1; n > -1; n-- )
      {
        e = Map.get( n );
        
        if( Address >= e.VPos && Address <= ( curVra.VPos + curVra.Len ) )
        {
          Index = n; curVra = e;
          
          super.seek( ( Address - e.VPos ) + e.Pos );
          
          VAddress = Address - super.getFilePointer();
          
          return;
        }
      }
    }
    
    VAddress = Address - super.getFilePointer(); fireIOEvent( new IOEvent( this, 0 ) );
  }
  
  public int readV() throws IOException
  {
    //Seek address if outside current address space.
    
    if( getVirtualPointer() > curVra.VEnd ) { seekV( getVirtualPointer() ); }
    
    //Read in current offset. If any data to be read.
    
    if( super.getFilePointer() >= curVra.Pos && super.getFilePointer() <= curVra.FEnd ) { return( super.read() ); }
    
    //No data then 0 space.
    
    VAddress += 1; return( 0 );
  }
  
  //Read len bytes from current virtual offset pointer.
  
  public int readV( byte[] b ) throws IOException
  {
    int Pos = 0, n = 0;
    
    //Seek address if outside current address space.
    
    if( getVirtualPointer() > curVra.VEnd ) { seekV( getVirtualPointer() ); }
    
    //Start reading.
    
    while( Pos < b.length )
    {
      //Read in current offset.
      
      if( super.getFilePointer() >= curVra.Pos && super.getFilePointer() <= curVra.FEnd && curVra.Len > 0 )
      {
        //Number of bytes that can be read from current area.
        
        n = (int)Math.min( ( curVra.FEnd + 1 ) - super.getFilePointer(), b.length );
        
        super.read( b, Pos, n ); Pos += n;
      }
      
      //Else 0 space. Skip n to Next address.
      
      else
      {
        n = (int)( curVra.VLen - ( super.getFilePointer() - curVra.Pos ) );
        
        if( n < 0 ) { n = b.length - Pos; }
        
        VAddress += n; Pos += n;
        
        seekV( getVirtualPointer() );
      }
    }
    
    return( 0 );
  }
  
  //Read len bytes at offset to len from current virtual offset pointer.
  
  public int readV( byte[] b, int off, int len ) throws IOException
  {
    int Pos = off, n = 0; len += off;
    
    //Seek address if outside current address space.
    
    if( getVirtualPointer() > curVra.VEnd ) { seekV( getVirtualPointer() ); }
    
    //Start reading.
    
    while( Pos < len )
    {
      //Read in current offset.
      
      if( super.getFilePointer() >= curVra.Pos && super.getFilePointer() <= curVra.FEnd && curVra.Len > 0 )
      {
        //Number of bytes that can be read from current area.
        
        n = (int)Math.min( ( curVra.FEnd + 1 ) - super.getFilePointer(), len );
        
        super.read( b, Pos, n ); Pos += n;
      }
      
      //Else 0 space. Skip n to Next address.
      
      else
      {
        n = (int)( curVra.VLen - ( super.getFilePointer() - curVra.Pos ) );
        
        if( n < 0 ) { n = len - Pos; }
        
        VAddress += n; Pos += n;
        
        seekV( getVirtualPointer() );
      }
    }
    
    return( 0 );
  }
  
  //Write an byte at Virtual address pointer if mapped.
  
  public void writeV( int b ) throws IOException
  {
    //Seek address if outside current address space.
    
    if( getVirtualPointer() > curVra.VEnd ) { seekV( getVirtualPointer() ); }
    
    //Write the byte if in range of address.
    
    if( super.getFilePointer() >= curVra.Pos && super.getFilePointer() <= curVra.FEnd ) { super.write( b ); return; }
    
    //Move virtual pointer.
    
    VAddress++;
  }
  
  //Write set of byte at Virtual address pointer to only mapped bytes.
  
  public void writeV( byte[] b ) throws IOException
  {
    int Pos = 0, n = 0;
    
    //Seek address if outside current address space.
    
    if( getVirtualPointer() > curVra.VEnd ) { seekV( getVirtualPointer() ); }
    
    //Start Writing.
    
    while( Pos < b.length )
    {
      //Write in current offset.
      
      if( super.getFilePointer() >= curVra.Pos && super.getFilePointer() <= curVra.FEnd && curVra.Len > 0 )
      {
        //Number of bytes that can be written in current area.
        
        n = (int)Math.min( ( curVra.FEnd + 1 ) - super.getFilePointer(), b.length );
        
        super.write( b, Pos, n ); Pos += n;
      }
      
      //Else 0 space. Skip n to Next address.
      
      else
      {
        n = (int)( curVra.VLen - ( super.getFilePointer() - curVra.Pos ) );
        
        if( n < 0 ) { n = b.length - Pos; }
        
        VAddress += n; Pos += n;
        
        seekV( getVirtualPointer() );
      }
    }
  }
  
  //Write len bytes at offset to len from current virtual offset pointer.
  
  public void writeV( byte[] b, int off, int len ) throws IOException
  {
    int Pos = off, n = 0; len += off;
    
    //Seek address if outside current address space.
    
    if( getVirtualPointer() > curVra.VEnd ) { seekV( getVirtualPointer() ); }
    
    //Start writing.
    
    while( Pos < len )
    {
      //Write in current offset.
      
      if( super.getFilePointer() >= curVra.Pos && super.getFilePointer() <= curVra.FEnd && curVra.Len > 0 )
      {
        //Number of bytes that can be written in current area.
        
        n = (int)Math.min( ( curVra.FEnd + 1 ) - super.getFilePointer(), len );
        
        super.write( b, Pos, n ); Pos += n;
      }
      
      //Else 0 space. Skip n to Next address.
      
      else
      {
        n = (int)( curVra.VLen - ( super.getFilePointer() - curVra.Pos ) );
        
        if( n < 0 ) { n = len - Pos; }
        
        VAddress += n; Pos += n;
        
        seekV( getVirtualPointer() );
      }
    }
  }
  
  //Debug The address mapped memory.
  
  public void Debug()
  {
    String s = "";
    
    for( int i = 0; i < MSize; s += Map.get( i++ ) + "\r\n" );
    
    System.out.println( s );
  }
}
