# RandomAccessFileV

This is the web version of RandomAccessFileV. It uses the <a href="https://developer.mozilla.org/en-US/docs/Web/API/FileReader">FileReader API</a> built into javascript for reading files.

This version is different, but designed to do event handling based IO, and Virtual address mapping as well.

The script exists as part of RandomAccessFileV, but is called FileReaderV to signify the difference.

Simulates a ram address space using very little ram Memory, and time on CPU.

This tool is very useful for mapping areas of an file at random positions and reading the data in order using Virtual read.

This tool is also useful for mapping parts of an program into a simulated virtual space that is to be modified, or changed. Thus loading all the patches between. Allowing you to write directly to the patches on disk when making changes, and to the program.

# Opening Binary File

We can have the user select which file they want to open using the input type file chooser. Which gives us a binary file object.

```html
<html>
  <body>
    <script type="text/javascript" src="FileReaderV.js"></script>
    <script>
      var file = new FileReaderV();
  
      //Read 100 bytes at offset 0x100 when file is chosen.
  
      function Open( f )
      {
        file.setTarget( f );

        //Call selected function dataRead when data is done being read at offset.

        file.Events = false; file.call(this, "dataRead");

        file.seek(0x100); //Offset to start reading.
          
        file.read(100); //Amount of data to read.

        file.Events = true;
      }
    
      //When data is read our function will be called.

      function dataRead(d)
      {
        alert( "Offset = " + d.offset + "\r\nBinary Data = " + d.data + "" );
      }
    </script>
    <input type="file" id="file" onchange="Open(this.files[0]);" />
  </body>
</html>
```

We can read bytes directly from a web file. This may be useful if you are designing custom binary formats to load on your page using javascript as a codec.

```html
<html>
  <body onload="init();">
    <script type="text/javascript" src="RandomAccessFileV/FileReaderV.js"></script>
    <script>
      var file = new FileReaderV();
  
      //This is the selected function to call when the HTML document loads.
  
      function init( f )
      {
        //Call the selected function once the file is requested.
        
        file.getFile("https://raw.githubusercontent.com/Recoskie/JDisassembly/master/JD-asm.jar", load);
      }

      //The function load is called when the file is successfully requested.

      function load( f )
      {
        Open(f);
      }

      //Read 100 bytes at offset 0x100.

      function Open( f )
      {
        file.setTarget( f );

        //Call selected function dataRead when data is done being read at offset.

        file.Events = false; file.call(this, "dataRead");

        file.seek(0x100); //Offset to start reading.
          
        file.read(100); //Amount of data to read.

        file.Events = true;
      }
    
      //When data is read our function will be called.

      function dataRead(d)
      {
        alert( "Offset = " + d.offset + "\r\nBinary Data = " + d.data + "" );
      }
    </script>
    <input type="file" id="file" onchange="Open(this.files[0]);" />
  </body>
</html>
```
Now if you want the file chooser to show the file we have opened on the web we need to do a file data transferrer to the file input element.

```html
<html>
  <body onload="init();">
    <script type="text/javascript" src="RandomAccessFileV/FileReaderV.js"></script>
    <script>
      var file = new FileReaderV();
  
      //This is the selected function to call when the HTML document loads.
  
      function init( f )
      {
        //Call the selected function once the file is requested.
        
        file.getFile("https://raw.githubusercontent.com/Recoskie/JDisassembly/master/JD-asm.jar", load);
      }

      //The function load is called when the file is successfully requested.

      function load( f )
      {
        //Sets the file object to the file chooser input.

        var d = new DataTransfer(); d.items.add(f); document.getElementById("file").files = d.files;

        Open(f);
      }

      //Read 100 bytes at offset 0x100.

      function Open( f )
      {
        file.setTarget( f );

        //Call selected function dataRead when data is done being read at offset.

        file.Events = false; file.call(this, "dataRead");

        file.seek(0x100); //Offset to start reading.
          
        file.read(100); //Amount of data to read.

        file.Events = true;
      }
    
      //When data is read our function will be called.

      function dataRead(d)
      {
        alert( "Offset = " + d.offset + "\r\nBinary Data = " + d.data + "" );
      }
    </script>
    <input type="file" id="file" onchange="Open(this.files[0]);" />
  </body>
</html>
```

# Raw Binary data

The file reader uses the File object. We can create raw binary files in raw javascript in RAM memory and read it as a file.

```html
<html>
  <body onload="init();">
    <script type="text/javascript" src="RandomAccessFileV/FileReaderV.js"></script>
    <script>
      var file = new FileReaderV();
  
      //This is the selected function to call when the HTML document loads.
  
      function init( f )
      { 
        //Create an binary data in RAM memory one byte at a time.

        var size = 16 ** 3, data = new Uint8Array(new ArrayBuffer(size));

        for (var i = 0; i < size; i++)
        {
          data[i] = i / 16;
        }

        //Create an make believe binary file that does not exist and give it to the file reader.

       load( new File([data],"RAM File.bin") );
      }

      //The function load is called when the file is successfully requested.

      function load( f )
      {
        //Sets the file object to the file chooser input.

        var d = new DataTransfer(); d.items.add(f); document.getElementById("file").files = d.files;

        Open(f);
      }

      //Read 100 bytes at offset 0x100.

      function Open( f )
      {
        file.setTarget( f );

        //Call selected function dataRead when data is done being read at offset.

        file.Events = false; file.call(this, "dataRead");

        file.seek(0x100); //Offset to start reading.
          
        file.read(100); //Amount of data to read.

        file.Events = true;
      }
    
      //When data is read our function will be called.

      function dataRead(d)
      {
        alert( "Offset = " + d.offset + "\r\nBinary Data = " + d.data + "" );
      }
    </script>
    <input type="file" id="file" onchange="Open(this.files[0]);" />
  </body>
</html>
```

The File chooser will recognize it as a Real file through file data transfer even though it only exists in RAM memory. This is useful if you want to write code that can manipulate or create binary files, or compile programs. You can have the user chose where to save or download the file using **saveAs**.

All of these web app features are supported across mobile phones and PC web browsers. We can process or do anything binary related locally on a phone, PC, or tablet.

We can even Take a File we generated one binary digit at a time in javascript and load it as a picture in a picture element on the page as long as you know how to encode some picture formats one pixel at a time in binary.

We can do the same with audio and video files.

You can create encoders and decoders that run fully in javascript for custom audio/video binary file formats.

Additionally, you can also write compilers that run in javascript as well and use **saveAs** to let the user save their compiled program on the device.

You can even create emulators that run using only javascript in a web browser and the FileReader API.

You can also create Files in RAM for the game's pictures and textures while emulating it. This has already been done actually.

# Creating IO data Components.

The file reader has an event system that calls methods **onread**, **onseek** to all components in the HTML document that work with data.

You can create your own component. The events are there so that all components graphically update when you seek or look at a particular data.

For now the event handler is not fully tested so documentation will come at a later time.
