/*------------------------------------------------------------
This allows us to work with many components on a single file reader and buffer with threaded event handling onread.
This is a cleaner implementation of the java version of RandomAccessFileV.java.
Does not support Virtual address mapping yet.
See https://github.com/Recoskie/RandomAccessFileV/
------------------------------------------------------------*/

function FileReaderV(file)
{
  this.file = (file instanceof File) ? file : new File([],""); this.offset = 0;
  
  this.comps = []; this.Events = true;
  
  this.data = [];
  
  this.fr.parent = this;
}

FileReaderV.prototype.setTarget = function(file)
{
  this.file = (file instanceof File) ? file : new File([],""); this.size = file.size;
}

//Returns file to select function.

FileReaderV.prototype.getFile = function(file, func)
{
  if(typeof(file) == "string")
  {
    var self = this, r = new XMLHttpRequest();
    
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

FileReaderV.prototype.call = function() { }

FileReaderV.prototype.read = function(size)
{
  this.fr.readAsArrayBuffer(this.file.slice(this.offset, this.offset + size));
}

FileReaderV.prototype.seek = function(pos)
{
  this.offset = pos < 0 ? 0 : pos;

  if( this.Events )
  {
    for( var i = 0; i < this.comps.length; i++ )
    {
      this.comps[i].onseek(this.parent);
    }
  }
}

FileReaderV.prototype.fr = new FileReader();

FileReaderV.prototype.fr.onload = function()
{
  this.parent.data = new Uint8Array(this.result);

  if( this.Events )
  {
    for( var i = 0; i < this.parent.comps.length; i++ )
    {
      this.parent.comps[i].onread(this.parent);
    }
  }
  else { this.parent.call.update(this.parent); }
}
        
FileReaderV.prototype.fr.onerror = function() { console.log("error"); }