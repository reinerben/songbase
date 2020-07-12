# SongBase
A tool to maintain and operate on playlists.

## Features
* Check if all songs are existing
* If songs are renamed or moved from one folder to another within the music folder tree it manages to change all affected playlists
* Shuffle playlist order. Interprets are considerd during shuffling so they don't follow each other
* Sort a playlist (case insensitive)
* Combine playlists to one list
* Remove content of one playlist from others
* Extract common entries of two playlists

Currently only m3u and m3u8 playlist formats are supported. Both without comment extensions.

## Usage
Usage: *songBase [\<options\>] [\<list\> ...]*

### Arguments:
*\<list\>* Path to a playlist or '*-*' if playlist should be read from standard input.
If the playlist path is prefixed by '*-*' this playlist file is read in but the result is written to standard output.
'*-*' or '*-\<list\>*' can only be specified once.
Otherwise some operations allow to specify more than one playlist.

Currently only m3u (ISO8859-1) and m3u8 (UTF-8) playlist types are supported.

### Options:
***--base \<dir\>***  
Base folder for searching playlists. If playlists *\<list\>* are specified it defaults to the folder of the first playlist. If '*-*' is specified it defaults to the current working directory.

***--dryrun***  
No changes are made. Only report what would be done.

***--nocheck***  
Don't check if a songs exists when reading in the playlists

***--nointerpret***  
During default map operation: don't check for interpret folders

***--rmsource***  
During map operation: delete a song in the source folder if it already exists in destination folder

***--sorted***  
All playlists which has to be written are sorted before writing them.
This also applies to standard output writes.
Case is ignored during sorting.

***--type \<type\>***  
Playlist type when reading from standard input and writing to standard output (defaults to m3u)

***--help***  
Display this help

### Operations:
***--map \<a\>=\<b\>***  
Move all songs from folder *\<a\>* found in playlist *\<list\>* to folder *\<b\>*.
Only one playlist argument is allowed.
All other playlists found in the base folder are updated to reflect this move.
This is the default operation if no other is given and songs are moved from folder '`Neu`' to '`Rock`'.
A special behavior in this default operation is that if there is a folder with the name of the interpret
then the song is moved to this folder instead of '`Rock`'.

***--check***  
Only check all playlists found in the base folder (defaults to working directory) if their songs exist.

***--sort***  
Sorts all playlists supplied as arguments.
If solely '*-*' or *\<list\>* is specified standard input resp. playlist *\<list\>* is sorted and written to standard output.

***--shuffle [\<gap\>]***  
Shuffles all playlists supplied as arguments.
If solely '*-*' or *-\<list\>* is specified standard input resp. playlist *\<list\>* is shuffled and written to standard output.
*\<gap\>* is an optional number of songs which should be between songs of same interpret (default: 5).
Please note that the gap also depends on the variety of interprets and may be lower than requested.

***--select \<text\>***  
Write entries of playlist *\<list\>* which contains text *\<text\>* to standard output.
Multiple playlist arguments are allowed.
The text is searched in the folder, interpret and title part.
The search is case sensitive.
                    
***--add \<list2\>***  
Add content of playlist *\<list2\>* to all playlists supplied as arguments.
If solely '-' or *-\<list\>* is specified the union of standard input resp. playlist *\<list\>* and *\<list2\>* are written to standard output.
If no playlist is supplied as argument the songs from *\<list2\>* are added to all playlists found in the base folder.
                    
***--remove \<list2\>***  
Remove content of playlist *\<list2\>* from all playlists supplied as arguments. 
If solely '-' or *-\<list\>* is specified the differences between standard input resp. playlist *\<list\>* and *\<list2\>* are written to standard output.
If no playlist is supplied as argument the songs from *\<list2\>* are removed from all playlists found in the base folder.
                    
***--union***  
Write content of all playlists supplied as argument to standard output.

***--intersect \<list2\>***  
Write common entries in playlist *\<list2\>* and playlist *\<list\>* to standard output. Only one playlist argument is allowed.
