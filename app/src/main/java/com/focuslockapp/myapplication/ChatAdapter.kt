package com.focuslockapp.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Simple Data Class for our messages
data class ChatMessage(val text: String, val isUser: Boolean)

class ChatAdapter(private val messages: ArrayList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBot: TextView = view.findViewById(R.id.tvBotMessage)
        val tvUser: TextView = view.findViewById(R.id.tvUserMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        if (message.isUser) {
            // Show User Bubble, Hide Bot Bubble
            holder.tvUser.text = message.text
            holder.tvUser.visibility = View.VISIBLE
            holder.tvBot.visibility = View.GONE
        } else {
            // Show Bot Bubble, Hide User Bubble
            holder.tvBot.text = message.text
            holder.tvBot.visibility = View.VISIBLE
            holder.tvUser.visibility = View.GONE
        }
    }

    override fun getItemCount() = messages.size
}