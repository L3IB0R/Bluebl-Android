package com.example.data

sealed interface Command {
    data object Help : Command
    data object Quit : Command
    data class Nick(val name: String) : Command
    data class Me(val action: String) : Command
    data object Who : Command
    data class Theme(val id: String?) : Command
    data class DensityCmd(val name: String) : Command
    data object Clear : Command
    data object Peers : Command
    data object Deps : Command
    data class Unknown(val name: String) : Command
}

object Commands {
    fun parse(input: String): Command? {
        if (!input.startsWith('/')) return null
        val rest = input.substring(1)
        if (rest.isEmpty()) return Command.Help
        val spaceIdx = rest.indexOf(' ')
        val cmd = (if (spaceIdx >= 0) rest.substring(0, spaceIdx) else rest).lowercase()
        val args = (if (spaceIdx >= 0) rest.substring(spaceIdx + 1) else "").trim()

        return when (cmd) {
            "help", "?" -> Command.Help
            "quit", "exit", "q" -> Command.Quit
            "nick" -> Command.Nick(args)
            "me" -> Command.Me(args)
            "who", "w" -> Command.Who
            "theme", "t" -> Command.Theme(args.ifEmpty { null })
            "density", "d" -> Command.DensityCmd(args)
            "clear" -> Command.Clear
            "peers", "nicks" -> Command.Peers
            "deps", "doctor" -> Command.Deps
            else -> Command.Unknown(cmd)
        }
    }

    fun helpEntries(): List<Pair<String, String>> = listOf(
        "/nick NOMBRE" to "Cambiar tu nombre visible en el chat",
        "/me ACCIÓN" to "Enviar acción en tercera persona",
        "/who · /peers" to "Ver dispositivos cercanos disponibles",
        "/theme [ID]" to "Abrir selector de temas o aplicar uno",
        "/density N" to "Espaciado: compacto · cómodo · amplio",
        "/clear" to "Limpiar los mensajes de la pantalla",
        "/deps" to "Comprobar estado de la radio Bluetooth",
        "/help" to "Mostrar esta lista de atajos"
    )
}
