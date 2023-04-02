/*-----------------------------------------------------------------------------------------------------------------------
This allows us to work with many components on a single file reader and buffer with threaded event handling onread.
This is a cleaner implementation of the java version of RandomAccessFileV.java.
See https://github.com/Recoskie/RandomAccessFileV/
-----------------------------------------------------------------------------------------------------------------------*/
  
//Positions of an file can be mapped into ram address space locations.

function VRA( Pos, Len, VPos, VLen )
{
  //Data offset length can't be higher than virtual offset length.
    
  if( Len > VLen ){ Len = VLen; }
  
  //Calculate file offset end positions and virtual end positions.
    
  this.FEnd = Pos + (Len > 0 ? ( Len - 1 ) : 0); this.VEnd = VPos + ( VLen - 1 );

  //Set mapped.

  this.Mapped = Len != 0; this.Pos = Pos; this.Len = Len; this.VPos = VPos; this.VLen = VLen;
}

//Set the end of an address when another address writes into this address.

VRA.prototype.setEnd = function( Address )
{
  if( this.VEnd <= Address ){ return; }

  //Set end of the current address to the start of added address.
      
  this.VEnd = Address;
      
  //Calculate address length.
      
  this.VLen = ( this.VEnd + 1 ) - this.VPos;
      
  //If there still is data after the added address.
      
  this.Len = ( this.Len < this.VLen ) ? this.Len : this.VLen;
      
  //Calculate the bytes written into.
      
  this.FEnd = this.Pos + (this.Len > 0 ? ( this.Len - 1 ) : 0);

  //Set mapped.

  this.Mapped = this.Len != 0;
}
    
//Addresses that write over the start of an address.
    
VRA.prototype.setStart = function( Address )
{
  if( this.VPos >= Address ){ return; }

  //Add Data offset to bytes written over at start of address.

  this.Pos += Address - this.VPos;
        
  //Move Virtual address start to end of address.
        
  this.VPos = Address;
        
  //Recalculate length between the new end position.
        
  this.Len = ( this.Pos > this.FEnd ) ? 0 : ( this.FEnd + 1 ) - this.Pos;
      
  if( this.Len == 0 ) { this.Pos = 0; }

  this.VLen = ( this.VPos > this.VEnd ) ? 0 : ( this.VEnd + 1 ) - this.VPos;

  //Set mapped.

  this.Mapped = this.Len != 0;
}

//Used to debug Virtual address space.

VRA.prototype.toString = function()
{
  return( "-----------------------------------------------------------------------------------------------------------------------\r\n" +
          "File(Offset)=" + this.Pos.address() + "---FileEnd(Offset)=" + this.FEnd.address() + "\r\n" + 
          "Start(Address)=" + this.VPos.address() + "---End(Address)=" + this.VEnd.address() + "\r\n" +
          "VLength=" + this.VLen.address() + "---FLength=" + this.Len.address() + "" );
}

//Construct file reader.

function FileReaderV(file)
{
  this.file = (file instanceof File) ? file : new File([],""); this.offset = 0; this.virtual = 0;
  
  this.comps = []; this.Events = true; this.temp = false;
  
  this.data = []; this.dataV = []; this.tempD = [];

  this.data.offset = 0; this.dataV.offset = 0; this.tempD.offset = 0;
  
  this.fr.parent = this.frv.parent = this;

  //The mapped addresses.
  
  this.Map = [ new VRA( 0, 0, 0, 0x20000000000000 ) ];
  
  //The virtual address that the current virtual address pointer is in range of.
  
  this.curVra = this.Map[0];
  
  //Speeds up search. By going up or down from current virtual address.
  
  this.Index = 0;
}

FileReaderV.prototype.setTarget = function(file)
{
  this.file = (file instanceof File) ? file : new File([],""); this.size = file.size; this.name = file.name;
}

//Returns file to select function.

FileReaderV.prototype.getFile = function(file, func)
{
  if(typeof(file) == "string")
  {
    var r = new XMLHttpRequest();
    
    r.open('GET', file, true); r.responseType = 'blob';
    
    r.onload = function()
    {
      func(new File([r.response],file));
    }
    
    r.send();
  }
  
  //Everything else then return an empty file.
  
  return( new File([],"") );
}

//This is used to call a method after reading data by a format reader, or data tool.
//The object reference and function name are needed in order to call the function with it's proper function and object reference.

FileReaderV.prototype.ref = function() { }; FileReaderV.prototype.func = ""; FileReaderV.prototype.arg = undefined;
FileReaderV.prototype.sRef = function() { }; FileReaderV.prototype.sFunc = ""; FileReaderV.prototype.arg = undefined;

FileReaderV.prototype.bufRead = function(obj, func, arg) { if( this.Events ) { this.Events = false; this.ref = obj; this.func = func; this.arg = arg; } };

FileReaderV.prototype.onRead = function(obj, func, arg) { if( this.Events ) { this.temp = !(this.Events = false); this.ref = obj; this.func = func; this.arg = arg; } };

FileReaderV.prototype.onSeek = function(obj, func){ this.sRef = obj; this.sFunc = func; }

//Add an virtual address.
  
FileReaderV.prototype.addV = function( Offset, DataLen, Address, AddressLen ) 
{
  //An address can use no data from the file with zero data length and be zero fill space, but it can never contain a zero address length.

  if( AddressLen == 0 ){ return; }

  //There is no need to address this high for any software in contrast to standard memory spacing of a compiled programs sections.
  //If this ever becomes an problem I will add full 64 bit addressing, but it is very unlikely even for terabyte big programs.

  if( (Address + AddressLen) > 0x001FFFFFFFFFFFFF  ) { console.error("Address out of Bounds!"); return; }

  //Create the address location as "Add" we use "CMP" to compare it against the address map.

  var Add = new VRA( Offset, DataLen, Address, AddressLen ), Cmp = null;
    
  //The numerical range the address lines up to in index in the address map.
    
  var e = 0;

  //Write in alignment.
    
  for( var i = 0; i < this.Map.length; i++ )
  {
    Cmp = this.Map[i];

    //Remove any mapped address that is overwritten.
    //The added address starts at, or before an address, then ends at, or past the address. 

    if( Add.VPos <= Cmp.VPos && Add.VEnd >= Cmp.VEnd ) { Map.splice(i, 1); e = i; i -= 1; }
      
    //If the added address writes to an address, but does not write over it fully.
    //Then the Added address position is at, or before the end of an address, then ends after, or at the start address of an address.
    
    else if( Add.VPos <= Cmp.VEnd && Add.VEnd >= Cmp.VPos ) //("start" <= End && "End" >= start) means it overlaps part of the compared address.
    {
      e = i;

      //Our address is less than the address end position that we are writing too.

      if( Add.VEnd < Cmp.VEnd )
      {
        //If our address is slightly after the start of an address then there is data that remains before our address.
        //We insert this data as a new address and set the end of the current address.
        //This allows us to make a space between address and to leave what remains.

        if( Add.VPos > Cmp.VPos )
        {
          //We put this reaming data as a new address with the end position before the start of our address we are adding.

          var t = new VRA( Cmp.Pos, Cmp.Len, Cmp.VPos, Cmp.VLen ); t.setEnd( Add.VPos - 1 ); this.Map.splice(i, 0, t); e = i + 1;
        }

        //Does not write past the compared address. So we set the stating byte to the end of our added address.

        Cmp.setStart( Add.VEnd + 1 );
      }

      //Writes to End, or past the compared address.
      //We then make the address length shorter. To allow our added address to start after it.
      
      if( Add.VPos > Cmp.VPos ) { Cmp.setEnd( Add.VPos - 1 ); e = i + 1; }
    }
  }

  this.Index = e; this.curVra = Add;

  //We palace the address in it's proper position after adjusting the address space to fit it.
    
  this.Map.splice( e, 0, Add );
  
  //Check if this effects the current virtual address buffer.
  //Instead of resetting the virtual buffer making us have to call initBufV we should read the data.
  
  if( Add.VPos >= this.dataV.offset && Add.VPos <= (this.dataV.offset + this.buf) )
  {
    this.dataV = []; this.dataV.offset = 0x20000000000000;
  }
}

//Reset the Virtual ram map.
  
FileReaderV.prototype.resetV = function()
{    
  this.Map = [ new VRA( 0, 0, 0, 0x20000000000000 ) ];
  
  this.Index = 0; this.curVra = this.Map[0]; this.dataV.length = 0;
}

FileReaderV.prototype.read = function(size)
{
  if( this.fr.readyState != 1 ) { this.fr.readAsArrayBuffer(this.file.slice(this.offset, this.offset+size)); }
}

//We map the reads we are going to be doing then precess then in virtual read mode.

FileReaderV.prototype.readV = function(size)
{
  if( !this.temp ) { this.dataV.length = 0; this.dataV.offset = this.virtual; } else { this.tempD.length = 0; this.tempD.offset = this.virtual; }

  //Seek address if outside current address space.
    
  if( this.virtual > this.curVra.VEnd || this.virtual < this.curVra.VEnd ) { this.seekV( this.virtual ); }

  //We reference the sections that are going to be read.

  var i = this.Index; Cmp = this.Map[i]; this.sects = [], end = this.virtual + size;

  //Reference all addresses within the alignment of our read.

  Cmp = this.Map[i++]; while( Cmp && end >= Cmp.VPos && this.virtual <= Cmp.VEnd ) { if(Cmp.Mapped) { this.sects[this.sects.length] = Cmp; } Cmp = this.Map[i++]; }

  //We are most likely not going to start at the beginning of an address.

  if(this.sects.length > 0) { Cmp = this.sects.shift(); Cmp = new VRA(Cmp.Pos,Cmp.Len,Cmp.VPos,Cmp.VLen); Cmp.setStart(this.virtual); this.sects.unshift(Cmp); }

  //We are most likely not going to read to the end of the last address.

  if(this.sects.length > 0) { Cmp = this.sects.pop(); Cmp = new VRA(Cmp.Pos,Cmp.Len,Cmp.VPos,Cmp.VLen); Cmp.setEnd(this.virtual + size); this.sects.push(Cmp); }

  //Read the virtual address mapped sections into buffer.

  if( this.sects.length > 0 && this.frv.readyState != 1 )
  {
    this.frv.readAsArrayBuffer(this.file.slice(this.sects[0].Pos, this.sects[0].Pos + this.sects[0].Len));
  }

  //Else Reached the end of the section read.

  else
  {
    if( this.Events )
    {
      for( var i = 0; i < this.comps.length; i++ )
      {
        if(this.comps[i].visible) { this.comps[i].onread(this); }
      }
    }
    else
    {
      this.Events = true; if( !this.temp ) { this.ref[this.func]( this.arg ); }
  
      else
      {
        this.virtual = this.oldVirtual; this.offset = this.oldOffset;
        this.temp = false; var t = []; t.offset = this.offset; this.ref[this.func](this.arg);
      }
    }
  }
}

FileReaderV.prototype.seek = function(pos)
{
  if( this.temp && this.oldOffset == -1 ) { this.oldOffset = this.offset; this.oldVirtual = this.virtual; }

  /*It is possible the same offsets are mapped to multiple VRA addresses.
  seek moves virtual address around in current VRA only.*/
  
  this.offset = pos < 0 ? 0 : pos;
  
  if( this.curVra.Mapped && pos <= this.curVra.FEnd && pos >= this.curVra.Pos )
  {
    this.virtual = ( pos - this.curVra.Pos ) + this.curVra.VPos;
  }

  //It is important that both the virtual and offset buffers are synchronized.

  if( this.Events ) { this.oldOffset = this.offset; this.oldVirtual = this.virtual; this.bufRead(this, "seekEventV"); this.initBufV(); }
}

FileReaderV.prototype.seekV = function(pos)
{
  if( this.temp && this.oldVirtual == -1 ) { this.oldOffset = this.offset; this.oldVirtual = this.virtual; }

  var r = 0;

  //If address is in range of current address index.
  
  if( pos >= this.curVra.VPos && pos <= this.curVra.VEnd )
  {
    r = pos - this.curVra.VPos; if( r >= this.curVra.Len ) { r = this.curVra.Len; }
    
    if( this.curVra.Len > 0 ) { this.offset = r + this.curVra.Pos; }
    
    this.virtual = pos;
  }
  
  //If address is grater than the next vra iterate up in indexes.
  
  else if( pos >= this.curVra.VEnd || this.Index == -1 )
  {
    var e = null;
    
    for( var n = this.Index + 1; n < this.Map.length; n++ )
    {
      e = this.Map[n];
      
      if( pos >= e.VPos && pos <= e.VEnd )
      {
        this.Index = n; this.curVra = e;

        r = pos - e.VPos; if( r >= e.Len ) { r = e.Len; }
        
        if( this.curVra.Len > 0 ) { this.offset = r + e.Pos; }
        
        this.virtual = pos;
      }
    }
  }
  
  //else iterate down in indexes.
  
  else if( pos <= this.curVra.VPos )
  {
    var e = null;
    
    for( var n = this.Index - 1; n > -1; n-- )
    {
      e = this.Map[n];
      
      if( pos >= e.VPos && pos <= e.VEnd )
      {
        this.Index = n; this.curVra = e;

        r = pos - e.VPos; if( r >= e.Len ) { r = e.Len; }
        
        if( this.curVra.Len > 0 ) { this.offset = r + e.Pos; }
        
        this.virtual = pos;
      }
    }
  }

  //if the current buffer is not within the seek location than it must be updated before calling events.
  
  if( this.Events ) { this.oldOffset = this.offset; this.oldVirtual = this.virtual; this.bufRead(this,"seekEventV"); this.initBufV(); }
}

FileReaderV.prototype.seekEventV = function() { this.bufRead(this, "seekEvent"); this.initBuf(); }

FileReaderV.prototype.seekEvent = function()
{
  if( this.oldOffset >= 0 ){ this.offset = this.oldOffset; this.virtual = this.oldVirtual; this.oldOffset = -1; this.oldVirtual = -1; }

  for( var i = 0; i < this.comps.length; i++ )
  {
    if(this.comps[i].visible) { this.comps[i].onseek(this); }
  }
  
  //After seek event completes event trigger.
  
  if( this.sFunc != "" ) { this.sRef[this.sFunc](); this.sFunc = ""; }
}

FileReaderV.prototype.fr = new FileReader(); FileReaderV.prototype.frv = new FileReader();

FileReaderV.prototype.sects = []; FileReaderV.prototype.sectN = 0;

FileReaderV.prototype.fr.onload = function()
{
  if( !this.parent.temp ) { this.parent.data = new Uint8Array(this.result); this.parent.data.offset = this.parent.offset; }
  else { this.parent.tempD = new Uint8Array(this.result); this.parent.tempD.offset = this.parent.offset; }

  if( this.parent.Events )
  {
    for( var i = 0; i < this.parent.comps.length; i++ )
    {
      this.parent.comps[i].onread(this.parent);
    }
  }
  else
  {
    this.parent.Events = true; if( !this.parent.temp ) { this.parent.ref[this.parent.func]( this.parent.arg ); }
  
    else
    {
      this.parent.virtual = this.parent.oldVirtual; this.parent.offset = this.parent.oldOffset;
      this.parent.temp = false; this.parent.ref[this.parent.func]( this.parent.arg );
    }
  }
}

FileReaderV.prototype.frv.onload = function()
{
  //Read data sections. Return after last section is read into data buffer V.

  var sectN = this.parent.sectN, map = this.parent.sects[sectN], sects = this.parent.sects.length, data = !this.parent.temp ? this.parent.dataV : this.parent.tempD;

  //Place current read data into virtual space.

  for( var buf = new Uint8Array(this.result), v = map.VPos - this.parent.virtual, i = 0; i < map.VLen; data[v++] = isNaN(buf[i]) ? 0 : buf[i], i++ );

  //Read next section.
    
  if( ( this.parent.sectN += 1 ) < sects ) { this.readAsArrayBuffer(this.parent.file.slice(this.parent.sects[this.parent.sectN].Pos, this.parent.sects[this.parent.sectN].Pos + this.parent.sects[this.parent.sectN].Len)); }

  //Else Reached the end of the section read.

  else
  {
    this.parent.sects = []; this.parent.sectN = 0; data.offset = this.parent.virtual;

    if( this.parent.Events )
    {
      for( var i = 0; i < this.parent.comps.length; i++ )
      {
        this.parent.comps[i].onread(this.parent);
      }
    }
    else
    {
      this.parent.Events = true; if( !this.parent.temp ) { this.parent.ref[this.parent.func]( this.parent.arg ); }
    
      else
      {
        this.parent.virtual = this.parent.oldVirtual; this.parent.offset = this.parent.oldOffset;
        this.parent.temp = false; this.parent.ref[this.parent.func]( this.parent.arg );
      }
    }
  }
}

FileReaderV.prototype.buf = 0; FileReaderV.prototype.oldVirtual = -1; FileReaderV.prototype.oldOffset = -1;

FileReaderV.prototype.oldE = function(){};

FileReaderV.prototype.setBuf = function(b, func)
{
  this.Events = true; this.buf = b;
  
  if( this.data.length > b && this.dataV.length > b ) { this.dataV.length = this.data.length = b; func(); }
  else
  {
    this.oldE = func || function(){}; this.oldOffset = this.offset; this.oldVirtual = this.virtual;
    this.bufRead( this, "lBufStart" ); this.offset -= this.offset & 0xF; this.read(this.buf);
  }
}

FileReaderV.prototype.lBufStart = function() { this.bufRead(this,"lBufEnd"); this.virtual -= this.virtual & 0xF; this.readV(this.buf); }

FileReaderV.prototype.lBufEnd = function() { this.virtual = this.oldVirtual; this.offset = this.oldOffset; this.oldE(); }

FileReaderV.prototype.initBuf = function()
{
  if( this.offset <= this.data.offset || this.offset >= (this.data.offset + this.data.length) )
  {
    this.offset -= this.offset & 0xF; this.read(this.buf);
  }
  else { this.Events = true; this.ref[this.func](this.arg); }
}

FileReaderV.prototype.initBufV = function()
{
  if( this.virtual <= this.dataV.offset || this.virtual >= (this.dataV.offset + this.dataV.length) )
  {
    this.virtual -= this.virtual & 0xF; this.readV(this.buf);
  }
  else { this.Events = true; this.ref[this.func](this.arg); }
}

//This is a function that waits till all data processing is finished before calling a method.

FileReaderV.prototype.wait = function(func, cb) { cb = cb instanceof FileReaderV ? cb : this; if( cb.Events ) { func(); return; } setTimeout(cb.wait,0,func,cb); }
        
FileReaderV.prototype.fr.onerror = FileReaderV.prototype.frv.onerror = function() { console.error("File IO Error!"); }

//Used only to debug address space.

FileReaderV.prototype.debug = function() { for( var i = 0, s = ""; i < this.Map.length; s += this.Map[i++] + "\r\n" ); console.log(s); }

//Address format offsets for debug output used by VRA toString.

if( Number.prototype.address == null )
{
  Number.prototype.address = function()
  {
    for( var s = this.toString(16).toUpperCase(); s.length < 16; s = "0" + s );
    return("0x"+s);
  }
}