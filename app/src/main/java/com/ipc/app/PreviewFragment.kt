package com.ipc.app

class PreviewFragment(private val activity: MainActiviy) {
    // Toda a lógica de preview vive aqui.
    // Atualmente o estado de preview é gerido diretamente no layout (activity_main.xml)
    // através de binding.previewState. Este ficheiro está preparado para receber
    // funcionalidades de preview futuras sem tocar no MainActiviy.
}