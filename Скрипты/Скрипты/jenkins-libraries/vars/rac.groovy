
def splitToKeyValue(line) {
    def map = [:]
    def key = line.split(':')[0].trim()
    def value = line.split(':', 2)[1].trim()
    map[key] = value
    return map
}

def parsingOutput(output, startsection, keyname, valuename) {
    // функция обработки вывода rac.exe
    // startsection - ключевое слово, имя параметра для позиционирования на нём как на первом в секциях вывода rac.exe
    // keyname - имя параметра значение которого устанавливается в качестве ключа пары ключ:значение
    // valuename - имя параметра значение которого устанавливается в качестве значения пары ключ:значение
    // возвращает коллекцию значений вида [keyname:valuename]
    def map = [:]
    def mapkey = [:]
    def mapvalue = [:]
    def lines = new String(output).split('\n')
    for (int i = 0; i<lines.size(); i++) {
        if (lines[i].trim().isEmpty()) {
            continue
        }
        def keyValue = splitToKeyValue(lines[i])
        if (keyValue[startsection] != null) {
            if (mapkey.isEmpty() == false && mapvalue.isEmpty() == false) {
                map.put(mapkey[keyname], mapvalue[valuename])
            }
            mapkey.clear()
            mapvalue.clear()
        }
        if (keyValue[keyname] != null) {
            mapkey[keyname] = keyValue[keyname]
        }
        if (keyValue[valuename] != null) {
            mapvalue[valuename] = keyValue[valuename]
        }
    }
    if (mapkey.isEmpty() == false && mapvalue.isEmpty() == false) {
        map.put(mapkey[keyname], mapvalue[valuename])
    }
    return map
}

def outputToMap(output, listKey) {
    // функция обработки вывода rac.exe
    // listKey - ключевое слово, имя параметра для позиционирования на нём как на первом в секциях вывода rac.exe,
	//           или список ключей по которому бедет собираться вывод.
    // возвращает массив коллекций [ключ:значение], значений секций вывода rac.exe
    def map = [:]
	def listMap = []
    def lines = new String(output).split('\n')
	
	if (listKey.getClass().toLower() == 'class java.util.arraylist') {
		if ((lines[0] =~ /$listKey[0] +:/).size() == 0) {
			throw new Exception("Неожиданное начало ответа rac.exe\n${lines[0]}")
		}
		def position = 0
		for (line in lines) {
			if (line.trim().isEmpty()) {
				continue
			}
			def keyValue = splitToKeyValue(line)
			if ((line =~ /${listKey[position]}\s+:/).size() > 0) {
				map.putAll(keyValue)
				position += 1
			}
			else {
				map[map.keySet()[position-1]] += line
				continue
			}
			if (position == listKey.size()) {
				position = 0
				listMap.add(map.clone())
				map.clear()
			}
		}
	}
	else {
		if ((lines[0] =~ /$listKey +:/).size() == 0) {
			throw new Exception("Неожиданное начало ответа rac.exe\n${lines[0]}")
		}
		for (line in lines) {
			if (line.trim().isEmpty()) {
				continue
			}
			def keyValue = splitToKeyValue(line)
			if (keyValue[listKey] != null) {
				if (map.isEmpty() == false) {
					listMap.add(map.clone())
					map.clear()
				}
			}
			map.putAll(keyValue)
		}
		if (map.isEmpty() == false) {
			listMap.add(map.clone())
		}		
	}
    return listMap
}
